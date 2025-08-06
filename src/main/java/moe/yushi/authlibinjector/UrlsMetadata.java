package moe.yushi.authlibinjector;

import java.util.Optional;

public class UrlsMetadata {

    public UrlsMetadata(Optional<String> apiURL, Optional<String> authserverUrl, Optional<String> sessionserverUrl, Optional<String> skinsUrl, Optional<String> minecraftservicesUrl) {
        this.apiURL = apiURL;
        this.authserverUrl = authserverUrl;
        this.sessionserverUrl = sessionserverUrl;
        this.skinsUrl = skinsUrl;
        this.minecraftservicesUrl = minecraftservicesUrl;
    }

    private final Optional<String> apiURL;
    private final Optional<String> authserverUrl;
    private final Optional<String> sessionserverUrl;
    private final Optional<String> skinsUrl;
    private final Optional<String> minecraftservicesUrl;

    public Optional<String> getApiURL() {
        return apiURL;
    }

    public Optional<String> getAuthserverUrl() {
        return authserverUrl;
    }

    public Optional<String> getSessionserverUrl() {
        return sessionserverUrl;
    }

    public Optional<String> getSkinsUrl() {
        return skinsUrl;
    }

    public Optional<String> getMinecraftservicesUrl() {
        return minecraftservicesUrl;
    }
}
