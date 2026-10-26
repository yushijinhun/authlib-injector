/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.util;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collection;

/** Real verification only; a failed key never prevents trying other key sizes. */
public final class SignatureVerifier {
	private SignatureVerifier() {}

	public static boolean verify(Collection<PublicKey> keys, byte[] data, byte[] signature) {
		if (data == null || signature == null || signature.length == 0 || signature.length > 1024) {
			return false;
		}
		for (PublicKey key : keys) {
			try {
				Signature verifier = Signature.getInstance("SHA1withRSA");
				verifier.initVerify(key);
				verifier.update(data);
				if (verifier.verify(signature)) return true;
			} catch (GeneralSecurityException ignored) {
				// Includes a signature made with a different RSA modulus size.
			}
		}
		return false;
	}
}
