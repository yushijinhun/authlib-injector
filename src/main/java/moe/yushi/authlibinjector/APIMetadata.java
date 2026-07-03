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
package moe.yushi.authlibinjector;

import static java.text.MessageFormat.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;

import java.io.UncheckedIOException;
import java.security.PublicKey;
import java.util.*;

import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.KeyUtils;

public class APIMetadata {

    public static APIMetadata parse(String apiRoot, String metadataResponse) throws UncheckedIOException {
        JSONObject response = asJsonObject(parseJson(metadataResponse));

        List<String> skinDomains =
                ofNullable(response.get("skinDomains"))
                        .map(it -> asJsonArray(it).stream()
                                .map(JsonUtils::asJsonString)
                                .collect(toList()))
                        .orElse(emptyList());

        Optional<PublicKey> decodedPublickey =
                ofNullable(response.get("signaturePublickey"))
                        .map(JsonUtils::asJsonString)
                        .map(KeyUtils::parseSignaturePublicKey);

        Map<String, Object> meta =
                ofNullable(response.get("meta"))
                        .map(it -> (Map<String, Object>) new TreeMap<>(asJsonObject(it)))
                        .orElse(emptyMap());

        Optional<Map<String, Object>> urlRedefinitions =
                ofNullable(response.get("urlsRedefining"))
                        .map(it -> new TreeMap<>(asJsonObject(it)));

        Optional<Map<String, Object>> fallbackUrlRedefinitions =
                ofNullable(response.get("fallbackUrlsRedefining"))
                        .map(it -> new TreeMap<>(asJsonObject(it)));

        return new APIMetadata(apiRoot, unmodifiableList(skinDomains), unmodifiableMap(meta), decodedPublickey, parseUrlsMetadata(urlRedefinitions), parseUrlsMetadata(fallbackUrlRedefinitions));
    }

    private String apiRoot;
    private Optional<UrlsMetadata> UrlsRedefining;
    private Optional<UrlsMetadata> FallbackUrlsRedefining;
    private List<String> skinDomains;
    private Optional<PublicKey> decodedPublickey;
    private Map<String, Object> meta;

    public APIMetadata(String apiRoot, List<String> skinDomains, Map<String, Object> meta, Optional<PublicKey> decodedPublickey, Optional<UrlsMetadata> urlsRedefining, Optional<UrlsMetadata> fallbackUrlsRedefining) {
        this.apiRoot = requireNonNull(apiRoot);
        this.skinDomains = requireNonNull(skinDomains);
        this.meta = requireNonNull(meta);
        this.decodedPublickey = requireNonNull(decodedPublickey);
        this.UrlsRedefining = requireNonNull(urlsRedefining);
        this.FallbackUrlsRedefining = requireNonNull(fallbackUrlsRedefining);
    }

    public String getApiRoot() {
        return apiRoot;
    }

    public Optional<UrlsMetadata> getUrlsRedefining() {
        return UrlsRedefining;
    }

    public Optional<UrlsMetadata> getFallbackUrlsRedefining() {
        return FallbackUrlsRedefining;
    }

    public List<String> getSkinDomains() {
        return skinDomains;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public Optional<PublicKey> getDecodedPublickey() {
        return decodedPublickey;
    }

    @Override
    public String toString() {
        return format("APIMetadata [apiRoot={0}, skinDomains={1}, decodedPublickey={2}, meta={3}]", apiRoot, skinDomains, decodedPublickey, meta);
    }

    private static Optional<UrlsMetadata> parseUrlsMetadata(Optional<Map<String, Object>> data) {
        if (!data.isPresent()) return Optional.empty();

        Optional<String> apiURL = Optional.empty();
        Optional<String> authserverUrl = Optional.empty();
        Optional<String> sessionserverUrl = Optional.empty();
        Optional<String> skinsUrl = Optional.empty();
        Optional<String> minecraftservicesUrl = Optional.empty();

        for (Map.Entry<String, Object> entity : data.get().entrySet()) {
            switch (entity.getKey()){
                case "api":
                    apiURL = Optional.of(JsonUtils.asJsonString(entity.getValue()));
                    break;
                case "authserver":
                    authserverUrl = Optional.of(JsonUtils.asJsonString(entity.getValue()));
                    break;
                case "sessionserver":
                    sessionserverUrl = Optional.of(JsonUtils.asJsonString(entity.getValue()));
                    break;
                case "skins":
                    skinsUrl = Optional.of(JsonUtils.asJsonString(entity.getValue()));
                    break;
                case "minecraftservices":
                    minecraftservicesUrl = Optional.of(JsonUtils.asJsonString(entity.getValue()));
                    break;
            }
        }

        return Optional.of(new UrlsMetadata(apiURL, authserverUrl, sessionserverUrl, skinsUrl, minecraftservicesUrl));
    }
}
