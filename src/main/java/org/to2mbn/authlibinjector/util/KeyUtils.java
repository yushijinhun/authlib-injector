package org.to2mbn.authlibinjector.util;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class KeyUtils {

	public static byte[] decodePublicKey(String pem) throws IllegalArgumentException {
		pem = pem.replace("\n", "");
		final String header = "-----BEGIN PUBLIC KEY-----";
		final String end = "-----END PUBLIC KEY-----";
		if (pem.startsWith(header) && pem.endsWith(end)) {
			return Base64.getDecoder()
					.decode(pem.substring(header.length(), pem.length() - end.length()));
		} else {
			throw new IllegalArgumentException("Bad key format");
		}
	}

	public static PublicKey loadX509PublicKey(byte[] encodedKey) throws GeneralSecurityException {
		return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
	}

	private KeyUtils() {}

}
