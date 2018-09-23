package moe.yushi.authlibinjector.transform;

import java.util.Map;

import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.httpd.LocalYggdrasilHandle;

public class LocalYggdrasilApiTransformUnit extends DomainBasedTransformUnit {

	private LocalYggdrasilHandle handle;

	public LocalYggdrasilApiTransformUnit(YggdrasilConfiguration config) {
		handle = new LocalYggdrasilHandle(config);

		Map<String, String> mapping = getDomainMapping();
		mapping.put("skins.minecraft.net", "skins");
	}

	@Override
	protected String getApiRoot() {
		handle.ensureStarted();
		return "http://127.0.0.1:" + handle.getLocalApiPort() + "/";
	}

	@Override
	public String toString() {
		return "Local Yggdrasil API Transformer";
	}
}
