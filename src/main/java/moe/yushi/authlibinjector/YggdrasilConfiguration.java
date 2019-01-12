/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.KeyUtils;

public class YggdrasilConfiguration {

	public static YggdrasilConfiguration parse(String apiRoot, String metadataResponse) throws UncheckedIOException {
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

		return new YggdrasilConfiguration(apiRoot, unmodifiableList(skinDomains), unmodifiableMap(meta), decodedPublickey);
	}

	private String apiRoot;
	private List<String> skinDomains;
	private Optional<PublicKey> decodedPublickey;
	private Map<String, Object> meta;

	public YggdrasilConfiguration(String apiRoot, List<String> skinDomains, Map<String, Object> meta, Optional<PublicKey> decodedPublickey) {
		this.apiRoot = requireNonNull(apiRoot);
		this.skinDomains = requireNonNull(skinDomains);
		this.meta = requireNonNull(meta);
		this.decodedPublickey = requireNonNull(decodedPublickey);
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

	public Optional<PublicKey> getDecodedPublickey() {
		return decodedPublickey;
	}

	@Override
	public String toString() {
		return format("YggdrasilConfiguration [apiRoot={0}, skinDomains={1}, decodedPublickey={2}, meta={3}]", apiRoot, skinDomains, decodedPublickey, meta);
	}

}
