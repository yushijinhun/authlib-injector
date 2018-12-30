package moe.yushi.authlibinjector.yggdrasil;

import java.util.Map;
import java.util.UUID;

public class GameProfile {

	public static class PropertyValue {
		public String value;
		public String signature;
	}

	public UUID id;
	public String name;
	public Map<String, PropertyValue> properties;
}
