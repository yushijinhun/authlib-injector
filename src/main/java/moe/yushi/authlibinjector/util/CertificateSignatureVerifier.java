/*
 * Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package moe.yushi.authlibinjector.util;

import static java.nio.charset.StandardCharsets.US_ASCII;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;
import moe.yushi.authlibinjector.transform.support.YggdrasilKeyTransformUnit;

/** Verifies the existing generic-v1 and UUID-bound-v2 proxy certificate formats. */
public final class CertificateSignatureVerifier {

	private CertificateSignatureVerifier() {}

	public static boolean verify(PublicKey key, long expiry, UUID holder, byte[] signature) {
		if (key == null || signature == null) {
			return false;
		}
		byte[] encoded = key.getEncoded();
		byte[] payload;
		if (holder == null) {
			String pem = "-----BEGIN RSA PUBLIC KEY-----\n"
					+ Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(encoded)
					+ "\n-----END RSA PUBLIC KEY-----\n";
			payload = (expiry + pem).getBytes(US_ASCII);
		} else {
			payload = ByteBuffer.allocate(24 + encoded.length)
					.putLong(holder.getMostSignificantBits()).putLong(holder.getLeastSignificantBits())
					.putLong(expiry).put(encoded).array();
		}
		try {
			Signature verifier = YggdrasilKeyTransformUnit.createCertificateSignature();
			verifier.update(payload);
			return verifier.verify(signature);
		} catch (GeneralSecurityException e) {
			return false;
		}
	}
}
