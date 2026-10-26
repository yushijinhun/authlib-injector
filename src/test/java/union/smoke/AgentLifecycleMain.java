/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package union.smoke;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.MinecraftServicesKeyInfo;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import moe.yushi.authlibinjector.transform.support.YggdrasilKeyTransformUnit;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.KeyUtils;
import moe.yushi.authlibinjector.yggdrasil.PublicKeys;
import net.md_5.bungee.EncryptionUtil;
import net.md_5.bungee.protocol.PlayerPublicKey;

/** No production mutation hooks: trust changes come from the mock backend HTTP endpoint. */
public final class AgentLifecycleMain {
	public static void main(String[] args) throws Exception {
		System.out.println("UNION_AGENT_MAIN_STARTED");
		String mode = args[11];
		if ("unused".equals(mode)) { success(); return; }
		if ("http-first".equals(mode)) {
			PublicKeys keys = readNativeKeys();
			check(keys.getProfilePropertyKeys().contains(KeyUtils.parseX509PublicKey(decode(args[4]))), "HTTP demand loads keys");
			AgentSmokeMain.main(args);
			return;
		}
		MinecraftServicesKeyInfo info = new MinecraftServicesKeyInfo();
		Property a = new Property(AgentSmokeMain.VALUE, args[1]);
		Property b = new Property(AgentSmokeMain.VALUE, args[2]);
		Signature reusable = info.signature();
		byte[] data = AgentSmokeMain.VALUE.getBytes(UTF_8);
		if ("unavailable".equals(mode)) {
			for (int i = 1; i <= 2; i++) {
				check(!info.validateProperty(new Property(AgentSmokeMain.VALUE, args[i])), "unavailable custom property issuer rejected");
				reusable.update(data);
				check(!reusable.verify(decode(args[i])), "unavailable custom certificate issuer rejected");
			}
			check(info.validateProperty(new Property(AgentSmokeMain.VALUE, args[3])), "metadata property fallback remains trusted");
			reusable.update(data);
			check(reusable.verify(decode(args[3])), "metadata certificate fallback remains trusted");
			success(); return;
		}
		check(info.validateProperty(a), "initial station A trust");
		reusable.update(data);
		IdentifiedKeyImpl velocity = new IdentifiedKeyImpl(KeyUtils.parseX509PublicKey(decode(args[5])),
				Instant.ofEpochMilli(Long.parseLong(args[10])), decode(args[7]));
		velocity.setTestHolder(UUID.fromString(args[9]));
		check(velocity.isSignatureValid(), "initial Velocity certificate trust");
		IdentifiedKeyImpl newlyApproved = new IdentifiedKeyImpl(KeyUtils.parseX509PublicKey(decode(args[5])),
				Instant.ofEpochMilli(Long.parseLong(args[10])), decode(args[8]));
		newlyApproved.setTestHolder(UUID.fromString(args[9]));
		check(!newlyApproved.isSignatureValid(), "initial Velocity negative result");
		if ("rotation".equals(mode)) check(b.isSignatureValid(null), "initial station B trust");
		rotate(args[12]);
		if ("miss".equals(mode)) {
			Thread.sleep(1100); // Past configured one-second miss cooldown, well before periodic refresh.
			check(b.isSignatureValid(null), "miss refresh must retry the original call");
		} else if ("rotation".equals(mode)) {
			PublicKey peer = KeyUtils.parseX509PublicKey(decode(args[4]));
			long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
			// Inspection only; this does not trigger a refresh or a failed signature.
			while (!YggdrasilKeyTransformUnit.getPublicKeys().getPlayerCertificateKeys().contains(peer) && System.nanoTime() < until) {
				Thread.sleep(10);
			}
			check(YggdrasilKeyTransformUnit.getPublicKeys().getPlayerCertificateKeys().contains(peer), "periodic update");
			check(!b.isSignatureValid(null) && info.validateProperty(a), "revoked property key");
			check(!reusable.verify(decode(args[1])), "previously created verifier must reject revoked certificate key");
			check(!velocity.isSignatureValid(), "Velocity must not retain its cached positive result");
			check(newlyApproved.isSignatureValid(), "Velocity must not retain its cached negative result");
			reusable.update(data);
			check(reusable.verify(decode(args[2])), "previously created verifier must accept newly approved certificate key");
			PublicKeys exposed = readNativeKeys();
			check(!exposed.getProfilePropertyKeys().contains(peer) && exposed.getPlayerCertificateKeys().contains(peer), "HTTP snapshot updated");
			PlayerPublicKey certificate = new PlayerPublicKey(decode(args[5]), Long.parseLong(args[10]), decode(args[8]));
			check(EncryptionUtil.check(certificate, UUID.fromString(args[9])), "proxy uses refreshed certificate keys");
		} else if ("expiry".equals(mode)) {
			Thread.sleep(3200); // Beyond configured three-second hard age; the backend now returns 503.
			check(!info.validateProperty(a), "stale property keys must be rejected");
			check(!reusable.verify(decode(args[1])), "stale certificate keys must be rejected");
			check(!velocity.isSignatureValid(), "Velocity must recheck cache age");
		} else {
			throw new AssertionError("Unknown lifecycle mode: " + mode);
		}
		success();
	}

	private static void rotate(String root) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) new URL(root + "_test/rotate").openConnection();
		connection.setConnectTimeout(2000); connection.setReadTimeout(2000);
		try { check(connection.getResponseCode() == 204, "test control endpoint"); } finally { connection.disconnect(); }
	}

	private static PublicKeys readNativeKeys() throws Exception {
		HttpURLConnection connection = (HttpURLConnection) new URL("https://api.minecraftservices.com/publickeys").openConnection();
		connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
		try (java.io.InputStream in = connection.getInputStream()) {
			check("no-store".equals(connection.getHeaderField("Cache-Control")), "no HTTP trust cache");
			return PublicKeys.parse(IOUtils.asString(IOUtils.asBytes(in)));
		} finally { connection.disconnect(); }
	}

	private static byte[] decode(String value) { return Base64.getDecoder().decode(value); }
	private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
	private static void success() { System.out.println("UNION_AGENT_SMOKE_OK"); }
}
