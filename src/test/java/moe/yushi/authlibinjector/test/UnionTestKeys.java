/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

public final class UnionTestKeys {
	public static final KeyPair SITE_A = generate(2048);
	public static final KeyPair SITE_B = generate(2048);
	public static final KeyPair UNKNOWN = generate(2048);

	private UnionTestKeys() {}

	public static KeyPair generate(int bits) {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(bits);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	public static byte[] sign(PrivateKey key, byte[] payload) throws Exception {
		Signature signature = Signature.getInstance("SHA1withRSA");
		signature.initSign(key);
		signature.update(payload);
		return signature.sign();
	}

	public static String pem(PublicKey key) {
		return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(key.getEncoded())
				+ "\n-----END PUBLIC KEY-----\n";
	}

	public static JSONObject response(PublicKey[] properties, PublicKey[] certificates) {
		JSONObject response = new JSONObject();
		response.put("profilePropertyKeys", entries(properties));
		response.put("playerCertificateKeys", entries(certificates));
		return response;
	}

	private static JSONArray entries(PublicKey[] keys) {
		JSONArray entries = new JSONArray();
		for (PublicKey key : keys) {
			JSONObject entry = new JSONObject();
			entry.put("publicKey", Base64.getEncoder().encodeToString(key.getEncoded()));
			entries.add(entry);
		}
		return entries;
	}
}
