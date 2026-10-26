/* Owned test fixture, not a copy of Mojang authlib. */
package com.mojang.authlib.properties;

import java.security.PublicKey;

/** Exact legacy method descriptors used to exercise the packaged transformer. */
public final class Property {
	private final String value, signature;
	public Property(String value, String signature) { this.value = value; this.signature = signature; }
	public String getValue() { return value; }
	public String getSignature() { return signature; }
	public boolean isSignatureValid(PublicKey ignored) {
		throw new AssertionError("Property was not transformed");
	}
}
