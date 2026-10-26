/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package union.smoke;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.MinecraftServicesKeyInfo;
import com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo;
import com.velocitypowered.proxy.crypto.IdentifiedKeyImpl;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.KeyUtils;
import moe.yushi.authlibinjector.yggdrasil.PublicKeys;
import net.md_5.bungee.EncryptionUtil;
import net.md_5.bungee.protocol.PlayerPublicKey;

/** Runs in a fresh JVM with only the shaded agent JAR and owned test fixtures. */
public final class AgentSmokeMain {
	public static final String VALUE = "union texture payload 中文";

	public static void main(String[] args) throws Exception {
		boolean union = Boolean.parseBoolean(args[0]);
		YggdrasilServicesKeyInfo oldInfo = new YggdrasilServicesKeyInfo();
		MinecraftServicesKeyInfo newInfo = new MinecraftServicesKeyInfo();
		for (int i = 1; i <= 3; i++) {
			Property property = new Property(VALUE, args[i]);
			boolean expected = i == 1 || (union && i == 2);
			check(property.isSignatureValid(null) == expected, "legacy property " + i);
			check(oldInfo.validateProperty(property) == expected, "yggdrasil property " + i);
			check(newInfo.validateProperty(property) == expected, "modern property " + i);
		}
		for (String signature : Arrays.asList(null, "!", "AA==")) {
			check(!newInfo.validateProperty(new Property(VALUE, signature)), "invalid property");
		}
		check(!newInfo.validateProperty(new Property(VALUE + "tampered", args[1])), "changed property");

		for (Signature verifier : Arrays.asList(oldInfo.signature(), newInfo.signature())) {
			for (int i = 1; i <= 3; i++) {
				verifier.update(VALUE.getBytes(UTF_8));
				check(verifier.verify(decode(args[i])) == (i == 1), "certificate purpose " + i);
			}
			verifier.update(VALUE.getBytes(UTF_8));
			check(!verifier.verify(new byte[] { 0 }), "dummy certificate rejected");
		}

		PublicKey peer = KeyUtils.parseX509PublicKey(decode(args[4]));
		PublicKey player = KeyUtils.parseX509PublicKey(decode(args[5]));
		UUID holder = UUID.fromString(args[9]);
		long expiry = Long.parseLong(args[10]);
		check(EncryptionUtil.check(new PlayerPublicKey(player.getEncoded(), expiry, decode(args[6])), null), "bungee v1");
		for (int i = 7; i <= 8; i++) {
			boolean expected = i == 7;
			PlayerPublicKey oldKey = new PlayerPublicKey(player.getEncoded(), expiry, decode(args[i]));
			net.md_5.bungee.protocol.data.PlayerPublicKey newKey = new net.md_5.bungee.protocol.data.PlayerPublicKey(
					player.getEncoded(), expiry, decode(args[i]));
			check(EncryptionUtil.check(oldKey, holder) == expected, "bungee old " + i);
			check(EncryptionUtil.check(newKey, holder) == expected, "bungee modern " + i);
			IdentifiedKeyImpl velocity = new IdentifiedKeyImpl(player, Instant.ofEpochMilli(expiry), decode(args[i]));
			check(Boolean.valueOf(expected).equals(velocity.validateData(holder)), "velocity " + i);
			check(Boolean.FALSE.equals(velocity.validateData(UUID.randomUUID())), "velocity UUID binding");
		}

		// These constants must be rewritten to the agent's local HTTP server.
		HttpURLConnection connection = (HttpURLConnection) new URL("https://api.minecraftservices.com/publickeys").openConnection();
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(3000);
		try (java.io.InputStream in = connection.getInputStream()) {
			PublicKeys keys = PublicKeys.parse(IOUtils.asString(IOUtils.asBytes(in)));
			check(keys.getProfilePropertyKeys().contains(peer) == union, "HTTP property keys");
			check(!keys.getPlayerCertificateKeys().contains(peer), "HTTP certificate isolation");
		} finally {
			connection.disconnect();
		}
		connection = (HttpURLConnection) new URL("https://api.minecraftservices.com/player/certificates").openConnection();
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(3000);
		connection.setRequestMethod("POST");
		try {
			check(connection.getResponseCode() == 204, "no fabricated chat certificate");
		} finally {
			connection.disconnect();
		}
		System.out.println("UNION_AGENT_SMOKE_OK");
	}

	private static byte[] decode(String value) { return Base64.getDecoder().decode(value); }
	private static void check(boolean passed, String label) {
		if (!passed) throw new AssertionError(label);
	}
}
