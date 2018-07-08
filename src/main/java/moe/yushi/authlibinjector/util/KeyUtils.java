package moe.yushi.authlibinjector.util;

import static moe.yushi.authlibinjector.util.IOUtils.newUncheckedIOException;
import static moe.yushi.authlibinjector.util.IOUtils.removeNewLines;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class KeyUtils {

	public static byte[] decodePEMPublicKey(String pem) throws IllegalArgumentException {
		pem = removeNewLines(pem);
		final String header = "-----BEGIN PUBLIC KEY-----";
		final String end = "-----END PUBLIC KEY-----";
		if (pem.startsWith(header) && pem.endsWith(end)) {
			return Base64.getDecoder()
					.decode(pem.substring(header.length(), pem.length() - end.length()));
		} else {
			throw new IllegalArgumentException("Bad key format");
		}
	}

	public static PublicKey parseX509PublicKey(byte[] encodedKey) throws GeneralSecurityException {
		return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
	}

	public static PublicKey parseSignaturePublicKey(String pem) throws UncheckedIOException {
		try {
			return parseX509PublicKey(decodePEMPublicKey(pem));
		} catch (IllegalArgumentException | GeneralSecurityException e) {
			throw newUncheckedIOException("Bad signature public key", e);
		}
	}

	private KeyUtils() {}

}
