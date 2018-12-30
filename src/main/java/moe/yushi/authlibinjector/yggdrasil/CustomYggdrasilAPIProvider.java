package moe.yushi.authlibinjector.yggdrasil;

import static moe.yushi.authlibinjector.util.UUIDUtils.toUnsignedUUID;

import java.util.UUID;

import moe.yushi.authlibinjector.YggdrasilConfiguration;

public class CustomYggdrasilAPIProvider implements YggdrasilAPIProvider {

	private String apiRoot;

	public CustomYggdrasilAPIProvider(YggdrasilConfiguration configuration) {
		this.apiRoot = configuration.getApiRoot();
	}

	@Override
	public String queryUUIDsByNames() {
		return apiRoot + "api/profiles/minecraft";
	}

	@Override
	public String queryProfile(UUID uuid) {
		return apiRoot + "sessionserver/session/minecraft/profile/" + toUnsignedUUID(uuid);
	}

	@Override
	public String toString() {
		return apiRoot;
	}
}
