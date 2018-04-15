package org.to2mbn.authlibinjector.util;

import static org.to2mbn.authlibinjector.util.IOUtils.newUncheckedIOException;
import java.io.UncheckedIOException;
import org.to2mbn.authlibinjector.internal.org.json.simple.JSONArray;
import org.to2mbn.authlibinjector.internal.org.json.simple.JSONObject;
import org.to2mbn.authlibinjector.internal.org.json.simple.JSONValue;
import org.to2mbn.authlibinjector.internal.org.json.simple.parser.ParseException;

public final class JsonUtils {

	public static Object parseJson(String jsonText) throws UncheckedIOException {
		try {
			return JSONValue.parse(jsonText);
		} catch (ParseException e) {
			throw newUncheckedIOException("Invalid JSON", e);
		}
	}

	public static JSONObject asObject(Object json) throws UncheckedIOException {
		return assertJson(json, JSONObject.class, "an object");
	}

	public static JSONArray asArray(Object json) throws UncheckedIOException {
		return assertJson(json, JSONArray.class, "an array");
	}

	public static String asString(Object json) throws UncheckedIOException {
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
