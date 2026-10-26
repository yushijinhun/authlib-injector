/*
 * Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package moe.yushi.authlibinjector.util;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Verification-only adapter. Bounded buffering allows the same payload to be
 * retried against refreshed keys, including when a Signature object is reused.
 */
public final class MultiKeySignature extends Signature {

	public static final int MAX_PAYLOAD_BYTES = 64 * 1024;
	private final BiPredicate<byte[], byte[]> verifier;
	private final ByteArrayOutputStream payload = new ByteArrayOutputStream();
	private boolean overflowed;

	public MultiKeySignature(List<PublicKey> keys) {
		this(fixedVerifier(keys));
	}

	public MultiKeySignature(BiPredicate<byte[], byte[]> verifier) {
		super("SHA1withRSA");
		this.verifier = java.util.Objects.requireNonNull(verifier);
		try {
			initVerify((PublicKey) null);
		} catch (InvalidKeyException e) {
			throw new IllegalArgumentException("Invalid trusted certificate key", e);
		}
	}

	private static BiPredicate<byte[], byte[]> fixedVerifier(List<PublicKey> keys) {
		List<PublicKey> snapshot = new ArrayList<>(keys);
		return (data, signature) -> SignatureVerifier.verify(snapshot, data, signature);
	}

	@Override
	protected void engineInitVerify(PublicKey ignored) throws InvalidKeyException {
		payload.reset();
		overflowed = false;
	}

	@Override
	protected void engineUpdate(byte value) throws SignatureException {
		checkSize(1);
		payload.write(value);
	}

	@Override
	protected void engineUpdate(byte[] data, int offset, int length) throws SignatureException {
		checkSize(length);
		payload.write(data, offset, length);
	}

	private void checkSize(int length) throws SignatureException {
		if (overflowed || length > MAX_PAYLOAD_BYTES - payload.size()) {
			overflowed = true;
			payload.reset();
			throw new SignatureException("Certificate payload exceeds " + MAX_PAYLOAD_BYTES + " bytes");
		}
	}

	@Override
	protected boolean engineVerify(byte[] signature) throws SignatureException {
		try {
			return !overflowed && signature != null && verifier.test(payload.toByteArray(), signature);
		} finally {
			payload.reset();
			overflowed = false;
		}
	}

	@Override
	protected void engineInitSign(PrivateKey key) throws InvalidKeyException {
		throw new InvalidKeyException("This adapter only verifies signatures");
	}

	@Override
	protected byte[] engineSign() throws SignatureException {
		throw new SignatureException("This adapter only verifies signatures");
	}

	@Override
	@Deprecated
	protected void engineSetParameter(String name, Object value) {
		throw new InvalidParameterException("Parameters are not supported");
	}

	@Override
	@Deprecated
	protected Object engineGetParameter(String name) {
		throw new InvalidParameterException("Parameters are not supported");
	}
}
