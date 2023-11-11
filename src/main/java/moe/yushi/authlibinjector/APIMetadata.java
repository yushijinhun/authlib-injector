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
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;
import java.io.UncheckedIOException;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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

		Optional<PublicKey> signaturePublickey =
				ofNullable(response.get("signaturePublickey"))
						.map(JsonUtils::asJsonString)
						.map(KeyUtils::parseSignaturePublicKey);

		Set<PublicKey> playerCertificateKeys =
				ofNullable(response.get("playerCertificateKeys"))
						.map(it -> asJsonArray(it).stream()
								.map(JsonUtils::asJsonString)
								.map(KeyUtils::parseSignaturePublicKey)
								.collect(toSet()))
						.orElseGet(HashSet::new);

		Set<PublicKey> profilePropertyKeys =
				ofNullable(response.get("profilePropertyKeys"))
						.map(it -> asJsonArray(it).stream()
								.map(JsonUtils::asJsonString)
								.map(KeyUtils::parseSignaturePublicKey)
								.collect(toSet()))
						.orElseGet(HashSet::new);

		signaturePublickey.ifPresent(playerCertificateKeys::add);
		signaturePublickey.ifPresent(profilePropertyKeys::add);

		Map<String, Object> meta =
				ofNullable(response.get("meta"))
						.map(it -> (Map<String, Object>) new TreeMap<>(asJsonObject(it)))
						.orElse(emptyMap());

		return new APIMetadata(apiRoot, unmodifiableList(skinDomains), unmodifiableMap(meta), unmodifiableSet(playerCertificateKeys), unmodifiableSet(profilePropertyKeys));
	}

	private String apiRoot;
	private List<String> skinDomains;
	private Set<PublicKey> playerCertificateKeys;
	private Set<PublicKey> profilePropertyKeys;
	private Map<String, Object> meta;

	public APIMetadata(String apiRoot, List<String> skinDomains, Map<String, Object> meta,
	                   Set<PublicKey> playerCertificateKeys, Set<PublicKey> profilePropertyKeys) {
		this.apiRoot = requireNonNull(apiRoot);
		this.skinDomains = requireNonNull(skinDomains);
		this.meta = requireNonNull(meta);
		this.playerCertificateKeys = requireNonNull(playerCertificateKeys);
		this.profilePropertyKeys = requireNonNull(profilePropertyKeys);
	}

	public String getApiRoot() {
		return apiRoot;
	}

	public List<String> getSkinDomains() {
		return skinDomains;
	}

	public Map<String, Object> getMeta() {
		return meta;
	}

	public Set<PublicKey> getPlayerCertificateKeys() {
		return playerCertificateKeys;
	}

	public Set<PublicKey> getProfilePropertyKeys() {
		return profilePropertyKeys;
	}

	@Override
	public String toString() {
		return format("APIMetadata [apiRoot={0}, skinDomains={1}, playerCertificateKeys={2}, profilePropertyKeys={3}, meta={4}]", apiRoot, skinDomains, playerCertificateKeys, profilePropertyKeys, meta);
	}
}
