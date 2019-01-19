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
package moe.yushi.authlibinjector.yggdrasil;

import static moe.yushi.authlibinjector.util.UUIDUtils.toUnsignedUUID;

import java.util.Map;
import java.util.UUID;

import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

public final class YggdrasilResponseBuilder {
	private YggdrasilResponseBuilder() {
	}

	public static String queryUUIDs(Map<String, UUID> result) {
		JSONArray response = new JSONArray();
		result.forEach((name, uuid) -> {
			JSONObject entry = new JSONObject();
			entry.put("id", toUnsignedUUID(uuid));
			entry.put("name", name);
			response.add(entry);
		});
		return response.toJSONString();
	}

	public static String queryProfile(GameProfile profile, boolean withSignature) {
		JSONObject response = new JSONObject();
		response.put("id", toUnsignedUUID(profile.id));
		response.put("name", profile.name);

		JSONArray properties = new JSONArray();
		profile.properties.forEach((name, value) -> {
			JSONObject entry = new JSONObject();
			entry.put("name", name);
			entry.put("value", value.value);
			if (withSignature && value.signature != null) {
				entry.put("signature", value.signature);
			}
			properties.add(entry);
		});
		response.put("properties", properties);

		return response.toJSONString();
	}
}
