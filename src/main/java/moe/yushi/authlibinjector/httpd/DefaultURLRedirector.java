/*
 * Copyright (C) 2020  Haowei Wen <yushijinhun@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package moe.yushi.authlibinjector.httpd;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.UrlsMetadata;

public class DefaultURLRedirector implements URLRedirector {

    private static final Map<String, DomainMapping> DOMAIN_MAPPINGS = new HashMap<>();

    private Map<String, String> domainMapping = new HashMap<>();
    private APIMetadata config;

    static {
        DOMAIN_MAPPINGS.put("api.mojang.com", new DomainMapping(UrlsMetadata::getApiURL, "api"));
        DOMAIN_MAPPINGS.put("authserver.mojang.com", new DomainMapping(UrlsMetadata::getAuthserverUrl, "authserver"));
        DOMAIN_MAPPINGS.put("sessionserver.mojang.com", new DomainMapping(UrlsMetadata::getSessionserverUrl, "sessionserver"));
        DOMAIN_MAPPINGS.put("skins.minecraft.net", new DomainMapping(UrlsMetadata::getSkinsUrl, "skins"));
        DOMAIN_MAPPINGS.put("api.minecraftservices.com", new DomainMapping(UrlsMetadata::getMinecraftservicesUrl, "minecraftservices"));
    }

    public DefaultURLRedirector(APIMetadata config) {
        this.config = config;
        initDomainMapping();
    }

    private void initDomainMapping() {
        String apiRoot = config.getApiRoot();

        Optional<UrlsMetadata> urlsRedefining = config.getUrlsRedefining();
        for (Map.Entry<String, DomainMapping> entry : DOMAIN_MAPPINGS.entrySet()) {
            String domain = entry.getKey();
            DomainMapping mapping = entry.getValue();

            String targetUrl = urlsRedefining.flatMap(mapping.urlProvider::getUrl).orElse(apiRoot + mapping.defaultPath);
            domainMapping.put(domain, targetUrl);
        }
    }

    @Override
    public Optional<String> redirect(String domain, String path) {
        String urlBasis = domainMapping.get(domain);
        if (urlBasis == null) {
            return Optional.empty();
        }

        return Optional.of(urlBasis + path);
    }

    @Override
    public boolean UseFallback(String domain) {
        if (!domainMapping.containsKey(domain)) return false;
        if (!config.getFallbackUrlsRedefining().isPresent()) return false;

        UrlsMetadata urls = config.getFallbackUrlsRedefining().get();
        DomainMapping mapping = DOMAIN_MAPPINGS.get(domain);

        Optional<String> url = mapping.urlProvider.getUrl(urls);
        if (!url.isPresent()) return false;

        domainMapping.put(domain, url.get());
        return true;
    }

    @FunctionalInterface
    private interface UrlProvider {
        Optional<String> getUrl(UrlsMetadata urls);
    }

    private static final class DomainMapping {
        final UrlProvider urlProvider;
        final String defaultPath;

        DomainMapping(UrlProvider urlProvider, String defaultPath) {
            this.urlProvider = urlProvider;
            this.defaultPath = defaultPath;
        }
    }

}
