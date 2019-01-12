/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
