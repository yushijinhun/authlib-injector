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
