/*
 * Copyright (C) 2025  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector.yggdrasil;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;
import java.io.UncheckedIOException;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.KeyUtils;

public final class PublicKeys {
	public static PublicKeys parse(String publicKeysResponse) throws UncheckedIOException {
		JSONObject response = asJsonObject(parseJson(publicKeysResponse));

		Set<PublicKey> playerCertificateKeys =
				ofNullable(response.get("playerCertificateKeys"))
						.map(JsonUtils::asJsonArray)
						.map(PublicKeys::parsePublicKeysArray)
						.orElseGet(HashSet::new);

		Set<PublicKey> profilePropertyKeys =
				ofNullable(response.get("profilePropertyKeys"))
						.map(JsonUtils::asJsonArray)
						.map(PublicKeys::parsePublicKeysArray)
						.orElseGet(HashSet::new);

		return new PublicKeys(profilePropertyKeys, playerCertificateKeys);
	}
	public static Set<PublicKey> parsePublicKeysArray(JSONArray array) throws UncheckedIOException {
		return array.stream()
				.map(JsonUtils::asJsonObject)
				.map(p -> p.get("publicKey"))
				.map(JsonUtils::asJsonString)
				.map(KeyUtils::parseSignaturePublicKeyBase64DER)
				.collect(toSet());
	}
	public PublicKeys(Set<PublicKey> profilePropertyKeys, Set<PublicKey> playerCertificateKeys) {
		this.profilePropertyKeys = requireNonNull(profilePropertyKeys);
		this.playerCertificateKeys = requireNonNull(playerCertificateKeys);
	}
	public Set<PublicKey> profilePropertyKeys;
	public Set<PublicKey> playerCertificateKeys;
}
