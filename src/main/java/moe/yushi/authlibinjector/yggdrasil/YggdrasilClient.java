package moe.yushi.authlibinjector.yggdrasil;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_JSON;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.getURL;
import static moe.yushi.authlibinjector.util.IOUtils.newUncheckedIOException;
import static moe.yushi.authlibinjector.util.IOUtils.postURL;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonString;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;
import static moe.yushi.authlibinjector.util.UUIDUtils.fromUnsignedUUID;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.GameProfile.PropertyValue;

public class YggdrasilClient {

	private YggdrasilAPIProvider apiProvider;

	public YggdrasilClient(YggdrasilAPIProvider apiProvider) {
		this.apiProvider = apiProvider;
	}

	public Map<String, UUID> queryUUIDs(Set<String> names) throws UncheckedIOException {
		String responseText;
		try {
			responseText = asString(postURL(
					apiProvider.queryUUIDsByNames(), CONTENT_TYPE_JSON,
					JSONArray.toJSONString(names).getBytes(UTF_8)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Logging.HTTPD.fine("Query UUIDs of " + names + " at [" + apiProvider + "], response: " + responseText);

		Map<String, UUID> result = new LinkedHashMap<>();
		for (Object rawProfile : asJsonArray(parseJson(responseText))) {
			JSONObject profile = asJsonObject(rawProfile);
			result.put(
					asJsonString(profile.get("name")),
					parseUnsignedUUID(asJsonString(profile.get("id"))));
		}
		return result;
	}

	public Optional<UUID> queryUUID(String name) throws UncheckedIOException {
		return Optional.ofNullable(queryUUIDs(singleton(name)).get(name));
	}

	public Optional<GameProfile> queryProfile(UUID uuid, boolean withSignature) throws UncheckedIOException {
		String url = apiProvider.queryProfile(uuid);
		if (withSignature) {
			url += "?unsigned=false";
		}
		String responseText;
		try {
			responseText = asString(getURL(url));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (responseText.isEmpty()) {
			Logging.HTTPD.fine("Query profile of [" + uuid + "] at [" + apiProvider + "], not found");
			return Optional.empty();
		}
		Logging.HTTPD.fine("Query profile of [" + uuid + "] at [" + apiProvider + "], response: " + responseText);

		return Optional.of(parseGameProfile(asJsonObject(parseJson(responseText))));
	}

	private GameProfile parseGameProfile(JSONObject json) {
		GameProfile profile = new GameProfile();
		profile.id = parseUnsignedUUID(asJsonString(json.get("id")));
		profile.name = asJsonString(json.get("name"));
		profile.properties = new LinkedHashMap<>();
		for (Object rawProperty : asJsonArray(json.get("properties"))) {
			JSONObject property = (JSONObject) rawProperty;
			PropertyValue entry = new PropertyValue();
			entry.value = asJsonString(property.get("value"));
			if (property.containsKey("signature")) {
				entry.signature = asJsonString(property.get("signature"));
			}
			profile.properties.put(asJsonString(property.get("name")), entry);
		}
		return profile;
	}

	private UUID parseUnsignedUUID(String uuid) throws UncheckedIOException {
		try {
			return fromUnsignedUUID(uuid);
		} catch (IllegalArgumentException e) {
			throw newUncheckedIOException(e.getMessage());
		}
	}
}
