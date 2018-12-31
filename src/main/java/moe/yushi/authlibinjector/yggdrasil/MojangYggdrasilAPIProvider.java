package moe.yushi.authlibinjector.yggdrasil;

import static moe.yushi.authlibinjector.util.UUIDUtils.toUnsignedUUID;

import java.util.UUID;

public class MojangYggdrasilAPIProvider implements YggdrasilAPIProvider {

	@Override
	public String queryUUIDsByNames() {
		return "https://api.mojang.com/profiles/minecraft";
	}

	@Override
	public String queryProfile(UUID uuid) {
		return "https://sessionserver.mojang.com/session/minecraft/profile/" + toUnsignedUUID(uuid);
	}

	@Override
	public String toString() {
		return "Mojang";
	}
}
