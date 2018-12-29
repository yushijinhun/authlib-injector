package moe.yushi.authlibinjector.httpd;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_JSON;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.getURL;
import static moe.yushi.authlibinjector.util.IOUtils.newUncheckedIOException;
import static moe.yushi.authlibinjector.util.IOUtils.postURL;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonString;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.Logging;

public class LegacySkinAPIFilter implements URLFilter {

	private static final Pattern PATH_SKINS = Pattern.compile("^/MinecraftSkins/(?<username>[^/]+)\\.png$");

	private YggdrasilConfiguration configuration;

	public LegacySkinAPIFilter(YggdrasilConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public boolean canHandle(String domain, String path) {
		return domain.equals("skins.minecraft.net");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) {
		if (!domain.equals("skins.minecraft.net"))
			return empty();
		Matcher matcher = PATH_SKINS.matcher(path);
		if (!matcher.find())
			return empty();
		String username = matcher.group("username");

		Optional<String> skinUrl;
		try {
			skinUrl = queryCharacterUUID(username)
					.flatMap(uuid -> queryCharacterProperty(uuid, "textures"))
					.map(encoded -> asString(Base64.getDecoder().decode(encoded)))
					.flatMap(texturesPayload -> obtainTextureUrl(texturesPayload, "SKIN"));
		} catch (UncheckedIOException e) {
			Logging.HTTPD.log(Level.WARNING, "Failed to fetch skin for " + username, e);
			return of(newFixedLengthResponse(Status.INTERNAL_ERROR, null, null));
		}

		if (skinUrl.isPresent()) {
			String url = skinUrl.get();
			Logging.HTTPD.fine("Retrieving skin for " + username + " from " + url);
			byte[] data;
			try {
				data = getURL(url);
			} catch (IOException e) {
				Logging.HTTPD.log(Level.WARNING, "Failed to retrieve skin from " + url, e);
				return of(newFixedLengthResponse(Status.INTERNAL_ERROR, null, null));
			}
			Logging.HTTPD.info("Retrieved skin for " + username + " from " + url + ", " + data.length + " bytes");
			return of(newFixedLengthResponse(Status.OK, "image/png", new ByteArrayInputStream(data), data.length));

		} else {
			Logging.HTTPD.info("No skin is found for " + username);
			return of(newFixedLengthResponse(Status.NOT_FOUND, null, null));
		}
	}

	private Optional<String> queryCharacterUUID(String username) throws UncheckedIOException {
		String responseText;
		try {
			responseText = asString(postURL(
					configuration.getApiRoot() + "api/profiles/minecraft",
					CONTENT_TYPE_JSON,
					JSONArray.toJSONString(singleton(username)).getBytes(UTF_8)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		Logging.HTTPD.fine("Query UUID of username " + username + ", response: " + responseText);

		JSONArray response = asJsonArray(parseJson(responseText));
		if (response.size() == 0) {
			return empty();
		} else if (response.size() == 1) {
			JSONObject profile = asJsonObject(response.get(0));
			return of(asJsonString(profile.get("id")));
		} else {
			throw newUncheckedIOException("Invalid JSON: Unexpected response length");
		}
	}

	private Optional<String> queryCharacterProperty(String uuid, String propertyName) throws UncheckedIOException {
		String responseText;
		try {
			responseText = asString(getURL(
					configuration.getApiRoot() + "sessionserver/session/minecraft/profile/" + uuid));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (responseText.isEmpty()) {
			Logging.HTTPD.fine("Query profile of " + uuid + ", not found");
			return empty();
		}
		Logging.HTTPD.fine("Query profile of " + uuid + ", response: " + responseText);

		JSONObject response = asJsonObject(parseJson(responseText));
		return asJsonArray(response.get("properties")).stream()
				.map(JsonUtils::asJsonObject)
				.filter(property -> asJsonString(property.get("name")).equals(propertyName))
				.findFirst()
				.map(property -> asJsonString(property.get("value")));
	}

	private Optional<String> obtainTextureUrl(String texturesPayload, String textureType) throws UncheckedIOException {
		JSONObject payload = asJsonObject(parseJson(texturesPayload));
		JSONObject textures = asJsonObject(payload.get("textures"));

		return ofNullable(textures.get(textureType))
				.map(JsonUtils::asJsonObject)
				.map(it -> ofNullable(it.get("url"))
						.map(JsonUtils::asJsonString)
						.orElseThrow(() -> newUncheckedIOException("Invalid JSON: Missing texture url")));
	}
}
