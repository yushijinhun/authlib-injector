package moe.yushi.authlibinjector.transform;

import java.util.Map;

import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.httpd.LocalYggdrasilHandle;
import moe.yushi.authlibinjector.util.Logging;

public class LocalYggdrasilApiTransformUnit extends DomainBasedTransformUnit {

	private LocalYggdrasilHandle handle;

	public LocalYggdrasilApiTransformUnit(YggdrasilConfiguration config) {
		handle = new LocalYggdrasilHandle(config);

		Map<String, String> mapping = getDomainMapping();
		if (Boolean.TRUE.equals(config.getMeta().get("feature.legacy_skin_api"))) {
			Logging.CONFIG.info("Disabled local redirect for legacy skin API, as the remote Yggdrasil server supports it");
		} else {
			mapping.put("skins.minecraft.net", "skins");
		}
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
