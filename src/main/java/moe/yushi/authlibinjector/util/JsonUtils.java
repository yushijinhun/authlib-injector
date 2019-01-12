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
package moe.yushi.authlibinjector.util;

import static moe.yushi.authlibinjector.util.IOUtils.newUncheckedIOException;
import java.io.UncheckedIOException;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONValue;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.ParseException;

public final class JsonUtils {

	public static Object parseJson(String jsonText) throws UncheckedIOException {
		try {
			return JSONValue.parse(jsonText);
		} catch (ParseException e) {
			throw newUncheckedIOException("Invalid JSON", e);
		}
	}

	public static JSONObject asJsonObject(Object json) throws UncheckedIOException {
		return assertJson(json, JSONObject.class, "an object");
	}

	public static JSONArray asJsonArray(Object json) throws UncheckedIOException {
		return assertJson(json, JSONArray.class, "an array");
	}

	public static String asJsonString(Object json) throws UncheckedIOException {
		return assertJson(json, String.class, "a string");
	}

	@SuppressWarnings("unchecked")
	private static <T> T assertJson(Object json, Class<T> type, String message) {
		if (type.isInstance(json)) {
			return (T) json;
		}
		throw newUncheckedIOException("Invalid JSON: not " + message + ": " + json);
	}

	private JsonUtils() {}

}
