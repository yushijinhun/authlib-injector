package moe.yushi.authlibinjector.util;

import java.util.UUID;

public final class UUIDUtils {

	public static String toUnsignedUUID(UUID uuid) {
		return uuid.toString().replace("-", "");
	}

	public static UUID fromUnsignedUUID(String uuid) {
		if (uuid.length() == 32) {
			return UUID.fromString(uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32));
		} else {
			throw new IllegalArgumentException("Invalid UUID: " + uuid);
		}
	}
	private UUIDUtils() {
	}
}
