/* Owned test fixture, not a copy of BungeeCord. */
package net.md_5.bungee;

import java.util.UUID;

public final class EncryptionUtil {
	public static boolean check(net.md_5.bungee.protocol.PlayerPublicKey key, UUID holder) {
		throw new AssertionError("Legacy Bungee check was not transformed");
	}
	public static boolean check(net.md_5.bungee.protocol.data.PlayerPublicKey key, UUID holder) {
		throw new AssertionError("Modern Bungee check was not transformed");
	}
}
