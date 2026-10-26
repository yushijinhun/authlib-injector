/* Owned test fixture, not a copy of Velocity. */
package com.velocitypowered.proxy.crypto;

import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;

public final class IdentifiedKeyImpl {
	public enum Revision { GENERIC_V1, LINKED_V2 }
	private final PublicKey key;
	private final Instant expiry;
	private final byte[] signature;
	private UUID holder;
	private Boolean cached;
	public IdentifiedKeyImpl(PublicKey key, Instant expiry, byte[] signature) {
		this.key = key; this.expiry = expiry; this.signature = signature;
	}
	public Revision getKeyRevision() { return Revision.LINKED_V2; }
	public PublicKey getSignedPublicKey() { return key; }
	public Instant getExpiryTemporal() { return expiry; }
	public byte[] getSignature() { return signature; }
	public UUID getSignatureHolder() { return holder; }
	public void setTestHolder(UUID holder) { this.holder = holder; }
	public boolean isSignatureValid() {
		if (cached == null) cached = validateData(holder);
		return Boolean.TRUE.equals(cached);
	}
	public Boolean validateData(UUID holder) {
		throw new AssertionError("Velocity check was not transformed");
	}
}
