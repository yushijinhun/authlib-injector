/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.yggdrasil;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.SignatureVerifier;

/**
 * Lazily activated, single-flight trust cache. Only the configured source can
 * replace the complete snapshot. No player-supplied issuer or URL is consulted.
 */
public final class PublicKeyManager implements AutoCloseable {

	public enum Purpose { PROFILE_PROPERTY, PLAYER_CERTIFICATE }

	@FunctionalInterface
	public interface KeySource {
		PublicKeys load() throws IOException;
	}

	public static final class Policy {
		public final long refreshMillis, maxAgeMillis, cooldownMillis, waitMillis;

		public Policy(long refreshMillis, long maxAgeMillis, long cooldownMillis, long waitMillis) {
			if (refreshMillis < 1 || maxAgeMillis < refreshMillis || maxAgeMillis > 86400000
					|| cooldownMillis < 1 || cooldownMillis > refreshMillis || waitMillis < 0 || waitMillis > 10000) {
				throw new IllegalArgumentException("Invalid public-key cache policy: 0 < cooldown <= refresh <= maxAge <= 1 day; wait is 0..10000 ms");
			}
			this.refreshMillis = refreshMillis;
			this.maxAgeMillis = maxAgeMillis;
			this.cooldownMillis = cooldownMillis;
			this.waitMillis = waitMillis;
		}

		public static Policy fromProperties(Properties properties) throws IOException {
			try {
				return new Policy(seconds(properties, "refreshSeconds", 300), seconds(properties, "maxAgeSeconds", 900),
						seconds(properties, "cooldownSeconds", 30), number(properties, "waitMillis", 2000, 0, 10000));
			} catch (IllegalArgumentException e) {
				throw new IOException("Invalid authlibinjector.publicKeys configuration", e);
			}
		}

		private static long seconds(Properties properties, String name, long fallback) {
			return number(properties, name, fallback, 1, 86400) * 1000;
		}

		private static long number(Properties properties, String name, long fallback, long min, long max) {
			String key = "authlibinjector.publicKeys." + name;
			String value = properties.getProperty(key);
			long result = value == null ? fallback : Long.parseLong(value);
			if (result < min || result > max) throw new IllegalArgumentException("Out of range: " + key);
			return result;
		}
	}

	private static final class Snapshot {
		final PublicKeys keys;
		final long fetchedAt;
		Snapshot(PublicKeys keys, long fetchedAt) { this.keys = keys; this.fetchedAt = fetchedAt; }
	}

	private final Object lock = new Object();
	private final PublicKeys baseline;
	private final PublicKeys refreshBaseline;
	private final KeySource source;
	private final Policy policy;
	private final LongSupplier clock;
	private final ScheduledExecutorService executor;
	private volatile Snapshot snapshot;
	private volatile boolean closed;
	// Guarded by lock. Time comes from nanoTime, never the wall clock.
	private CompletableFuture<Snapshot> inFlight;
	private ScheduledFuture<?> periodic;
	private boolean attempted;
	private long lastAttemptAt;

	private PublicKeyManager(PublicKeys baseline, PublicKeys refreshBaseline, KeySource source, Policy policy, LongSupplier clock) {
		this.baseline = requireNonNull(baseline);
		this.refreshBaseline = requireNonNull(refreshBaseline);
		this.source = source;
		this.policy = requireNonNull(policy);
		this.clock = requireNonNull(clock);
		this.executor = source == null ? null : Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "authlib-injector-public-keys");
			thread.setDaemon(true);
			return thread;
		});
	}

	public static PublicKeyManager fixed(PublicKeys keys) {
		return new PublicKeyManager(keys, keys, null, new Policy(300000, 900000, 30000, 0), System::nanoTime);
	}

	public static PublicKeyManager refreshing(PublicKeys baseline, KeySource source, Policy policy) {
		return refreshing(baseline, baseline, source, policy, System::nanoTime);
	}

	// Injectable monotonic clock keeps expiry/concurrency tests deterministic.
	static PublicKeyManager refreshing(PublicKeys baseline, KeySource source, Policy policy, LongSupplier clock) {
		return refreshing(baseline, baseline, source, policy, clock);
	}

	static PublicKeyManager refreshing(PublicKeys baseline, PublicKeys refreshBaseline, KeySource source,
			Policy policy, LongSupplier clock) {
		return new PublicKeyManager(baseline, refreshBaseline, requireNonNull(source), policy, clock);
	}

	public static PublicKeyManager forMetadata(APIMetadata metadata) throws IOException {
		if (!PublicKeyLoader.isEnabled(metadata)) return fixed(PublicKeyLoader.load(metadata));
		PublicKeyLoader.validateEndpoint(metadata.getApiRoot());
		// 故障或缓存过期时保留元数据签名根；刷新成功后只保留内置 Mojang 根和接口当前公布的密钥。
		java.util.List<PublicKey> siteKey = new ArrayList<>();
		if (metadata.getDecodedPublickey().isPresent()) {
			siteKey.add(metadata.getDecodedPublickey().get());
		}
		PublicKeys metadataKeys = new PublicKeys(siteKey, siteKey);
		PublicKeys mojang = PublicKeys.mojang();
		return refreshing(mojang.merge(metadataKeys), mojang, () -> PublicKeyLoader.load(metadata),
				Policy.fromProperties(System.getProperties()), System::nanoTime);
	}

	public boolean isDynamic() { return source != null; }

	/** Inspection only: does not start networking and never exposes expired custom keys. */
	public PublicKeys currentKeys() {
		return effectiveKeys(snapshot);
	}

	private PublicKeys effectiveKeys(Snapshot value) {
		if (closed || value == null || clock.getAsLong() - value.fetchedAt >= MILLISECONDS.toNanos(policy.maxAgeMillis)) {
			return baseline;
		}
		return value.keys;
	}

	/** Native /publickeys worker: wait only if no usable remote snapshot exists. */
	public PublicKeys getKeys() {
		CompletableFuture<Snapshot> pending = request(false);
		if (effectiveKeys(snapshot) == baseline) await(pending);
		return currentKeys();
	}

	/** The returned future cannot be used by callers to complete the internal flight. */
	public CompletableFuture<PublicKeys> refreshAsync() {
		return request(true).thenApply(ignored -> currentKeys());
	}

	public boolean verify(Purpose purpose, byte[] data, byte[] signature) {
		requireNonNull(purpose);
		if (closed || data == null || signature == null || signature.length == 0 || signature.length > 1024) return false;
		if (source == null) return verifyCurrent(purpose, data, signature);
		Snapshot observed = snapshot;
		CompletableFuture<Snapshot> pending = request(false);
		if (verifyCurrent(purpose, data, signature)) return true;
		// A first fetch or a concurrent periodic refresh may already be in flight.
		// Never launch another request just because that request finished meanwhile.
		if (snapshot == observed) pending = request(true);
		await(pending);
		return verifyCurrent(purpose, data, signature);
	}

	private boolean verifyCurrent(Purpose purpose, byte[] data, byte[] signature) {
		for (int attempt = 0; attempt < 2; attempt++) {
			if (closed) return false;
			Snapshot captured = snapshot;
			PublicKeys keys = effectiveKeys(captured);
			List<PublicKey> candidates = purpose == Purpose.PROFILE_PROPERTY
					? keys.getProfilePropertyKeys() : keys.getPlayerCertificateKeys();
			boolean valid = SignatureVerifier.verify(candidates, data, signature);
			// Do not accept a key removed by a refresh or expired during verification.
			if (captured == snapshot && keys == effectiveKeys(captured)) return !closed && valid;
		}
		return false;
	}

	private CompletableFuture<Snapshot> request(boolean onFailure) {
		synchronized (lock) {
			if (closed || source == null) return CompletableFuture.completedFuture(snapshot);
			if (periodic == null) {
				// No task (or thread) exists before the first real use of this manager.
				periodic = executor.scheduleWithFixedDelay(() -> request(false), policy.cooldownMillis,
						policy.cooldownMillis, MILLISECONDS);
			}
			if (inFlight != null) return inFlight;
			long now = clock.getAsLong();
			if (snapshot != null && !onFailure && now - snapshot.fetchedAt < MILLISECONDS.toNanos(policy.refreshMillis)) {
				return CompletableFuture.completedFuture(snapshot);
			}
			if (attempted && now - lastAttemptAt < MILLISECONDS.toNanos(policy.cooldownMillis)) {
				return CompletableFuture.completedFuture(snapshot);
			}
			attempted = true;
			lastAttemptAt = now;
			CompletableFuture<Snapshot> flight = new CompletableFuture<>();
			inFlight = flight;
			executor.execute(() -> load(flight));
			return flight;
		}
	}

	private void load(CompletableFuture<Snapshot> flight) {
		try {
			PublicKeys loaded = refreshBaseline.merge(requireNonNull(source.load()));
			Snapshot fresh = new Snapshot(loaded, clock.getAsLong());
			synchronized (lock) {
				if (closed || inFlight != flight) return;
				snapshot = fresh; // Replace, never accumulate removed/revoked keys.
				inFlight = null;
			}
			flight.complete(fresh);
			Logging.log(INFO, "Refreshed trusted public keys: " + loaded.getProfilePropertyKeys().size()
					+ " profile property, " + loaded.getPlayerCertificateKeys().size() + " player certificate");
		} catch (Exception e) {
			synchronized (lock) {
				if (closed || inFlight != flight) return;
				inFlight = null;
			}
			flight.completeExceptionally(e);
			// An error does not renew the age of the previous successful snapshot.
			Logging.log(WARNING, "Public key refresh failed; expired/untrusted keys remain rejected: " + e.getMessage());
		}
	}

	private void await(CompletableFuture<Snapshot> pending) {
		if (policy.waitMillis == 0 || pending.isDone()) return;
		try {
			pending.get(policy.waitMillis, MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException | TimeoutException ignored) {
			// Do not cancel a shared flight. Other callers and the next check can use it.
		}
	}

	@Override
	public void close() {
		CompletableFuture<Snapshot> pending;
		synchronized (lock) {
			closed = true;
			pending = inFlight;
			inFlight = null;
			if (periodic != null) periodic.cancel(false);
		}
		if (pending != null) pending.completeExceptionally(new IOException("Public key manager closed"));
		if (executor != null) executor.shutdownNow();
	}
}
