/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.yggdrasil;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static moe.yushi.authlibinjector.test.UnionTestKeys.*;
import static moe.yushi.authlibinjector.yggdrasil.PublicKeyManager.Purpose.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import moe.yushi.authlibinjector.util.MultiKeySignature;

public class PublicKeyManagerTest {
	private static final byte[] PAYLOAD = "signed data".getBytes(UTF_8);
	private static final PublicKeys BUILTIN = PublicKeys.mojang();
	private static final PublicKeyManager.Policy POLICY = new PublicKeyManager.Policy(300000, 900000, 30000, 2000);
	private final AtomicLong clock = new AtomicLong();
	private final List<PublicKeyManager> managers = new ArrayList<>();

	@AfterEach
	public void close() { managers.forEach(PublicKeyManager::close); }

	@Test
	public void fetchFailureDegradesToMetadataKeyBaseline() throws Exception {
		// 降级基线 = [元数据 signaturePublickey ∪ Mojang]：公钥接口故障时，
		// 本站自身签名的材质仍可验证，不再退化为仅 Mojang 导致全站材质失效。
		java.util.List<java.security.PublicKey> metaKeys = java.util.Collections.singletonList(
				moe.yushi.authlibinjector.test.UnionTestKeys.SITE_A.getPublic());
		PublicKeys baseline = PublicKeys.mojang().merge(new PublicKeys(metaKeys, metaKeys));
		PublicKeyManager manager = PublicKeyManager.refreshing(baseline,
				() -> { throw new java.io.IOException("key endpoint down"); }, POLICY, clock::get);
		managers.add(manager);

		byte[] sig = sign(moe.yushi.authlibinjector.test.UnionTestKeys.SITE_A.getPrivate(), PAYLOAD);
		assertTrue(manager.verify(PublicKeyManager.Purpose.PROFILE_PROPERTY, PAYLOAD, sig));

		byte[] foreign = sign(moe.yushi.authlibinjector.test.UnionTestKeys.UNKNOWN.getPrivate(), PAYLOAD);
		assertFalse(manager.verify(PublicKeyManager.Purpose.PROFILE_PROPERTY, PAYLOAD, foreign));
	}

	@Test
	public void successfulRefreshDropsMetadataFallbackButRetainsMojangKeys() throws Exception {
		PublicKeys fallback = BUILTIN.merge(keys(SITE_A.getPublic()));
		PublicKeyManager manager = PublicKeyManager.refreshing(fallback, BUILTIN,
				() -> keys(SITE_B.getPublic()), POLICY, clock::get);
		managers.add(manager);

		manager.refreshAsync().get(2, SECONDS);
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()),
				"A successful snapshot must not keep the legacy metadata fallback key");
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedB()));
		assertTrue(manager.currentKeys().getProfilePropertyKeys().containsAll(BUILTIN.getProfilePropertyKeys()));
	}

	private PublicKeyManager manager(PublicKeyManager.KeySource source) {
		PublicKeyManager manager = PublicKeyManager.refreshing(BUILTIN, source, POLICY, clock::get);
		managers.add(manager);
		return manager;
	}

	private void advance(long millis) { clock.addAndGet(MILLISECONDS.toNanos(millis)); }

	private static PublicKeys keys(PublicKey... keys) {
		return new PublicKeys(Arrays.asList(keys), Arrays.asList(keys));
	}

	private static byte[] signedA() throws Exception { return sign(SITE_A.getPrivate(), PAYLOAD); }
	private static byte[] signedB() throws Exception { return sign(SITE_B.getPrivate(), PAYLOAD); }

	@Test
	public void lazyFirstUseDownloadsOffCallerThreadAndCaches() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		AtomicReference<Thread> loaderThread = new AtomicReference<>();
		PublicKeyManager manager = manager(() -> {
			calls.incrementAndGet(); loaderThread.set(Thread.currentThread());
			return keys(SITE_A.getPublic(), SITE_B.getPublic());
		});
		assertEquals(0, calls.get());
		assertFalse(manager.currentKeys().getProfilePropertyKeys().contains(SITE_A.getPublic()));
		assertEquals(0, calls.get(), "Inspection is not a demand for keys");
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedB()));
		assertNotEquals(Thread.currentThread(), loaderThread.get());
		assertTrue(loaderThread.get().isDaemon());
		for (int i = 0; i < 10; i++) assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		assertEquals(1, calls.get());
	}

	@Test
	public void unknownSignatureRefreshesApprovedListAndRetriesSameCall() throws Exception {
		AtomicReference<PublicKeys> published = new AtomicReference<>(keys(SITE_A.getPublic()));
		AtomicInteger calls = new AtomicInteger();
		PublicKeyManager manager = manager(() -> { calls.incrementAndGet(); return published.get(); });
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		published.set(keys(SITE_A.getPublic(), SITE_B.getPublic()));
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedB()), "Cooldown is not bypassed by an unknown signature");
		advance(30000);
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedB()));
		assertEquals(2, calls.get());
	}

	@Test
	public void concurrentMissesJoinOneRequest() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		PublicKeyManager manager = manager(() -> {
			calls.incrementAndGet(); entered.countDown(); awaitLatch(release);
			return keys(SITE_A.getPublic());
		});
		ExecutorService callers = Executors.newFixedThreadPool(12);
		try {
			byte[] signature = signedA();
			List<Future<Boolean>> checks = new ArrayList<>();
			for (int i = 0; i < 24; i++) checks.add(callers.submit(() -> manager.verify(PROFILE_PROPERTY, PAYLOAD, signature)));
			assertTrue(entered.await(2, SECONDS));
			List<CompletableFuture<PublicKeys>> joined = new ArrayList<>();
			for (int i = 0; i < 24; i++) joined.add(manager.refreshAsync());
			assertEquals(1, calls.get());
			release.countDown();
			for (Future<Boolean> check : checks) assertTrue(check.get(3, SECONDS));
			for (CompletableFuture<PublicKeys> future : joined) assertTrue(future.get(3, SECONDS).getProfilePropertyKeys().contains(SITE_A.getPublic()));
			assertEquals(1, calls.get());
		} finally {
			release.countDown(); callers.shutdownNow();
		}
	}

	@Test
	public void invalidSignatureStormIsRateLimitedIncludingAfterErrors() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		PublicKeyManager manager = manager(() -> { calls.incrementAndGet(); throw new IOException("offline"); });
		byte[] signature = signedB();
		for (int i = 0; i < 100; i++) assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
		assertEquals(1, calls.get());
		advance(29999);
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
		assertEquals(1, calls.get());
		advance(1);
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
		assertEquals(2, calls.get());
	}

	@Test
	public void withdrawnKeysAreRemovedNotMergedAndPurposesCanChange() throws Exception {
		AtomicReference<PublicKeys> published = new AtomicReference<>(keys(SITE_A.getPublic(), SITE_B.getPublic()));
		PublicKeyManager manager = manager(published::get);
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedB()));
		published.set(new PublicKeys(Collections.singletonList(SITE_A.getPublic()), Collections.singletonList(SITE_B.getPublic())));
		advance(30000);
		manager.refreshAsync().get(2, SECONDS);
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedB()));
		assertTrue(manager.verify(PLAYER_CERTIFICATE, PAYLOAD, signedB()));
		assertFalse(manager.verify(PLAYER_CERTIFICATE, PAYLOAD, signedA()));
		assertTrue(manager.currentKeys().getProfilePropertyKeys().containsAll(BUILTIN.getProfilePropertyKeys()));
	}

	@Test
	public void revocationDuringVerificationCannotReturnOldPositiveResult() throws Exception {
		CountDownLatch insideVerify = new CountDownLatch(1), release = new CountDownLatch(1);
		RSAPublicKey slowKey = pausedKey(insideVerify, release);
		AtomicReference<PublicKeys> published = new AtomicReference<>(keys(slowKey));
		PublicKeyManager manager = manager(published::get);
		manager.refreshAsync().get(2, SECONDS);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			byte[] signature = signedA();
			Future<Boolean> result = caller.submit(() -> manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
			assertTrue(insideVerify.await(2, SECONDS));
			published.set(keys());
			advance(30000);
			manager.refreshAsync().get(2, SECONDS);
			release.countDown();
			assertFalse(result.get(2, SECONDS));
		} finally { release.countDown(); caller.shutdownNow(); }
	}

	@Test
	public void expiryDuringVerificationCannotReturnOldPositiveResult() throws Exception {
		CountDownLatch insideVerify = new CountDownLatch(1), release = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		PublicKeyManager manager = manager(() -> {
			if (calls.incrementAndGet() > 1) throw new IOException("offline");
			return keys(pausedKey(insideVerify, release));
		});
		manager.refreshAsync().get(2, SECONDS);
		ExecutorService caller = Executors.newSingleThreadExecutor();
		try {
			byte[] signature = signedA();
			Future<Boolean> result = caller.submit(() -> manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
			assertTrue(insideVerify.await(2, SECONDS));
			advance(900000);
			release.countDown();
			assertFalse(result.get(2, SECONDS));
		} finally { release.countDown(); caller.shutdownNow(); }
	}

	@Test
	public void closeDuringVerificationRejectsEvenFixedTrust() throws Exception {
		CountDownLatch insideVerify = new CountDownLatch(1), release = new CountDownLatch(1);
		try (PublicKeyManager manager = PublicKeyManager.fixed(keys(pausedKey(insideVerify, release)))) {
			ExecutorService caller = Executors.newSingleThreadExecutor();
			try {
				byte[] signature = signedA();
				Future<Boolean> result = caller.submit(() -> manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
				assertTrue(insideVerify.await(2, SECONDS));
				manager.close();
				release.countDown();
				assertFalse(result.get(2, SECONDS));
			} finally { release.countDown(); caller.shutdownNow(); }
		}
	}

	@Test
	public void reusedCertificateVerifierDoesNotRetainRevokedSnapshot() throws Exception {
		AtomicReference<PublicKeys> published = new AtomicReference<>(keys(SITE_A.getPublic()));
		PublicKeyManager manager = manager(published::get);
		Signature verifier = new MultiKeySignature((data, signature) -> manager.verify(PLAYER_CERTIFICATE, data, signature));
		verifier.update(PAYLOAD);
		assertTrue(verifier.verify(signedA()));
		verifier.update(PAYLOAD); // Even a payload begun before revocation must use the current trust set.
		published.set(keys(SITE_B.getPublic()));
		advance(30000);
		manager.refreshAsync().get(2, SECONDS);
		assertFalse(verifier.verify(signedA()));
		verifier.update(PAYLOAD);
		assertTrue(verifier.verify(signedB()));
	}

	@Test
	public void failedRefreshNeverExtendsMaximumAge() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		PublicKeyManager manager = manager(() -> {
			if (calls.incrementAndGet() > 1) throw new IOException("offline");
			return keys(SITE_A.getPublic());
		});
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		advance(890000);
		assertThrows(ExecutionException.class, () -> manager.refreshAsync().get(2, SECONDS));
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()), "Last known keys are usable only within maxAge");
		advance(10000);
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()), "Hard age applies even during cooldown");
		assertEquals(BUILTIN.getProfilePropertyKeys(), manager.currentKeys().getProfilePropertyKeys());
		assertEquals(2, calls.get());
	}

	@Test
	public void successfulRefreshRenewsAgeButDoesNotCacheSignatureResults() throws Exception {
		PublicKeyManager manager = manager(() -> keys(SITE_A.getPublic()));
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		advance(899000);
		manager.refreshAsync().get(2, SECONDS);
		advance(2000);
		assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		assertFalse(manager.verify(PROFILE_PROPERTY, "tampered".getBytes(UTF_8), signedA()));
	}

	@Test
	public void periodicRefreshWorksWithoutAnyVerificationFailure() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch secondLoad = new CountDownLatch(1);
		try (PublicKeyManager manager = PublicKeyManager.refreshing(BUILTIN, () -> {
			if (calls.incrementAndGet() == 1) return keys(SITE_A.getPublic());
			secondLoad.countDown(); return keys();
		}, new PublicKeyManager.Policy(80, 1000, 20, 500))) {
			assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
			assertTrue(secondLoad.await(2, SECONDS), "Periodic refresh must run without another call");
			eventually(() -> !manager.currentKeys().getProfilePropertyKeys().contains(SITE_A.getPublic()));
			assertTrue(calls.get() >= 2);
		}
	}

	@Test
	public void zeroWaitReturnsImmediatelyAndSubsequentCheckUsesCompletedFetch() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		try (PublicKeyManager manager = PublicKeyManager.refreshing(BUILTIN, () -> {
			awaitLatch(release); return keys(SITE_A.getPublic());
		}, new PublicKeyManager.Policy(300000, 900000, 30000, 0))) {
			long start = System.nanoTime();
			assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
			assertTrue(System.nanoTime() - start < SECONDS.toNanos(1));
			CompletableFuture<PublicKeys> joined = manager.refreshAsync();
			release.countDown();
			joined.get(2, SECONDS);
			assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		} finally { release.countDown(); }
	}

	@Test
	public void boundedWaitDoesNotCancelTheSharedFetch() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		try (PublicKeyManager manager = PublicKeyManager.refreshing(BUILTIN, () -> {
			awaitLatch(release); return keys(SITE_A.getPublic());
		}, new PublicKeyManager.Policy(300000, 900000, 30000, 25))) {
			long start = System.nanoTime();
			assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
			assertTrue(System.nanoTime() - start < SECONDS.toNanos(1));
			CompletableFuture<PublicKeys> joined = manager.refreshAsync();
			assertFalse(joined.isDone());
			release.countDown();
			joined.get(2, SECONDS);
			assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		} finally { release.countDown(); }
	}

	@Test
	public void expiryIsEnforcedWhileRefreshIsStuck() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		CountDownLatch release = new CountDownLatch(1), entered = new CountDownLatch(1);
		try (PublicKeyManager manager = PublicKeyManager.refreshing(BUILTIN, () -> {
			if (calls.incrementAndGet() > 1) { entered.countDown(); awaitLatch(release); }
			return keys(SITE_A.getPublic());
		}, new PublicKeyManager.Policy(300000, 900000, 30000, 25), clock::get)) {
			manager.refreshAsync().get(2, SECONDS);
			advance(900000);
			CompletableFuture<PublicKeys> pending = manager.refreshAsync();
			assertTrue(entered.await(2, SECONDS));
			assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
			assertFalse(pending.isDone());
			assertFalse(manager.currentKeys().getProfilePropertyKeys().contains(SITE_A.getPublic()));
			release.countDown();
			pending.get(2, SECONDS);
			assertTrue(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
		} finally { release.countDown(); }
	}

	@Test
	public void callerCannotCompleteSharedFlightWithUnapprovedKeys() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		PublicKeyManager manager = manager(() -> { awaitLatch(release); return keys(SITE_A.getPublic()); });
		try {
			CompletableFuture<PublicKeys> tampered = manager.refreshAsync();
			tampered.complete(keys(UNKNOWN.getPublic()));
			assertFalse(manager.currentKeys().getProfilePropertyKeys().contains(UNKNOWN.getPublic()));
			CompletableFuture<PublicKeys> genuine = manager.refreshAsync();
			assertFalse(genuine.isDone());
			release.countDown();
			assertFalse(genuine.get(2, SECONDS).getProfilePropertyKeys().contains(UNKNOWN.getPublic()));
		} finally { release.countDown(); }
	}

	@Test
	public void closeUnblocksWaitersAndStopsPublishing() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		PublicKeyManager manager = manager(() -> { entered.countDown(); awaitLatch(release); return keys(SITE_A.getPublic()); });
		CompletableFuture<PublicKeys> pending = manager.refreshAsync();
		assertTrue(entered.await(2, SECONDS));
		manager.close();
		release.countDown();
		assertThrows(ExecutionException.class, () -> pending.get(2, SECONDS));
		assertFalse(manager.currentKeys().getProfilePropertyKeys().contains(SITE_A.getPublic()));
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signedA()));
	}

	@Test
	public void interruptedWaitPreservesInterruptFlag() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		PublicKeyManager manager = manager(() -> { awaitLatch(release); return keys(SITE_A.getPublic()); });
		byte[] signature = signedA();
		try {
			Thread.currentThread().interrupt();
			assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, signature));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted(); release.countDown();
		}
	}

	@Test
	public void malformedSignatureDoesNotActivateNetwork() {
		AtomicInteger calls = new AtomicInteger();
		PublicKeyManager manager = manager(() -> { calls.incrementAndGet(); return keys(SITE_A.getPublic()); });
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, null));
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, new byte[0]));
		assertFalse(manager.verify(PROFILE_PROPERTY, PAYLOAD, new byte[1025]));
		assertEquals(0, calls.get());
	}

	@Test
	public void policyRejectsUnboundedOrInconsistentSettings() throws Exception {
		PublicKeyManager.Policy defaults = PublicKeyManager.Policy.fromProperties(new Properties());
		assertEquals(300000, defaults.refreshMillis);
		assertEquals(900000, defaults.maxAgeMillis);
		assertEquals(30000, defaults.cooldownMillis);
		assertEquals(2000, defaults.waitMillis);
		for (String[] invalid : new String[][] { { "waitMillis", "-1" }, { "waitMillis", "10001" },
				{ "maxAgeSeconds", "299" }, { "refreshSeconds", "10" }, { "cooldownSeconds", "0" },
				{ "maxAgeSeconds", "9223372036854775807" }, { "refreshSeconds", "abc" } }) {
			Properties properties = new Properties();
			properties.setProperty("authlibinjector.publicKeys." + invalid[0], invalid[1]);
			assertThrows(IOException.class, () -> PublicKeyManager.Policy.fromProperties(properties));
		}
	}

	private static RSAPublicKey pausedKey(CountDownLatch insideVerify, CountDownLatch release) {
		RSAPublicKey realKey = (RSAPublicKey) SITE_A.getPublic();
		return new RSAPublicKey() {
			private static final long serialVersionUID = 1L;
			@Override public BigInteger getModulus() {
				insideVerify.countDown();
				try { awaitLatch(release); } catch (IOException e) { throw new IllegalStateException(e); }
				return realKey.getModulus();
			}
			@Override public BigInteger getPublicExponent() { return realKey.getPublicExponent(); }
			@Override public String getAlgorithm() { return realKey.getAlgorithm(); }
			@Override public String getFormat() { return realKey.getFormat(); }
			@Override public byte[] getEncoded() { return realKey.getEncoded(); }
		};
	}

	private static void awaitLatch(CountDownLatch latch) throws IOException {
		try {
			if (!latch.await(5, SECONDS)) throw new IOException("Test latch timed out");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); throw new IOException(e);
		}
	}

	private static void eventually(BooleanSupplier condition) throws InterruptedException {
		long until = System.nanoTime() + SECONDS.toNanos(2);
		while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(5);
		assertTrue(condition.getAsBoolean());
	}
}
