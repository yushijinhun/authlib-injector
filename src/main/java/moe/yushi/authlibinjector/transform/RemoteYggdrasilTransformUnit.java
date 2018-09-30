package moe.yushi.authlibinjector.transform;

import java.util.Map;

public class RemoteYggdrasilTransformUnit extends DomainBasedTransformUnit {

	private String apiRoot;

	public RemoteYggdrasilTransformUnit(String apiRoot) {
		this.apiRoot = apiRoot;

		Map<String, String> mapping = getDomainMapping();
		mapping.put("api.mojang.com", "api");
		mapping.put("authserver.mojang.com", "authserver");
		mapping.put("sessionserver.mojang.com", "sessionserver");
		mapping.put("skins.minecraft.net", "skins");
	}

	@Override
	protected String getApiRoot() {
		return apiRoot;
	}

	@Override
	public String toString() {
		return "Yggdrasil API Transformer";
	}
}
