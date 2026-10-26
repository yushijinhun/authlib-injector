/* Owned test fixture, not a copy of BungeeCord. */
package net.md_5.bungee.protocol;

public class PlayerPublicKey {
	private final byte[] key, signature;
	private final long expiry;
	public PlayerPublicKey(byte[] key, long expiry, byte[] signature) {
		this.key = key; this.expiry = expiry; this.signature = signature;
	}
	public byte[] getKey() { return key; }
	public byte[] getSignature() { return signature; }
	public long getExpiry() { return expiry; }
}
