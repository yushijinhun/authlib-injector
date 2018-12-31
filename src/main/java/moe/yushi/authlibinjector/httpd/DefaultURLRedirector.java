package moe.yushi.authlibinjector.httpd;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import moe.yushi.authlibinjector.YggdrasilConfiguration;

public class DefaultURLRedirector implements URLRedirector {

	private Map<String, String> domainMapping = new HashMap<>();
	private String apiRoot;

	public DefaultURLRedirector(YggdrasilConfiguration config) {
		initDomainMapping();

		apiRoot = config.getApiRoot();
	}

	private void initDomainMapping() {
		domainMapping.put("api.mojang.com", "api");
		domainMapping.put("authserver.mojang.com", "authserver");
		domainMapping.put("sessionserver.mojang.com", "sessionserver");
		domainMapping.put("skins.minecraft.net", "skins");
	}

	@Override
	public Optional<String> redirect(String domain, String path) {
		String subdirectory = domainMapping.get(domain);
		if (subdirectory == null) {
			return Optional.empty();
		}

		return Optional.of(apiRoot + subdirectory + path);
	}

}
