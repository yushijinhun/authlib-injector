package moe.yushi.authlibinjector.httpd;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
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
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.Logging;

public class DeprecatedApiHttpd extends NanoHTTPD {

	public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

	// ^/MinecraftSkins/([^/]+)\.png$
	private static final Pattern URL_SKINS = Pattern.compile("^/MinecraftSkins/(?<username>[^/]+)\\.png$");

	private YggdrasilConfiguration configuration;

	public DeprecatedApiHttpd(int port, YggdrasilConfiguration configuration) {
		super("127.0.0.1", port);
		this.configuration = configuration;
	}

	@Override
	public Response serve(IHTTPSession session) {
		return processAsSkin(session)
				.orElseGet(() -> super.serve(session));
	}

	private Optional<Response> processAsSkin(IHTTPSession session) {
		Matcher matcher = URL_SKINS.matcher(session.getUri());
		if (!matcher.find()) return empty();
		String username = matcher.group("username");

		Optional<String> skinUrl;
		try {
			skinUrl = queryCharacterUUID(username)
					.flatMap(uuid -> queryCharacterProperty(uuid, "textures"))
					.map(encoded -> asString(Base64.getDecoder().decode(encoded)))
					.flatMap(texturesPayload -> obtainTextureUrl(texturesPayload, "SKIN"));
		} catch (UncheckedIOException e) {
			Logging.HTTPD.log(Level.WARNING, "unable to fetch skin for " + username, e);
			return of(newFixedLengthResponse(Status.INTERNAL_ERROR, null, null));
		}

		if (skinUrl.isPresent()) {
			String url = skinUrl.get();
			Logging.HTTPD.fine("retrieving skin for " + username + " from " + url);
			byte[] data;
			try {
				data = getURL(url);
			} catch (IOException e) {
				Logging.HTTPD.log(Level.WARNING, "unable to retrieve skin from " + url, e);
				return of(newFixedLengthResponse(Status.INTERNAL_ERROR, null, null));
			}
			Logging.HTTPD.info("retrieved skin for " + username + " from " + url + ", " + data.length + " bytes");
			return of(newFixedLengthResponse(Status.OK, "image/png", new ByteArrayInputStream(data), data.length));

		} else {
			Logging.HTTPD.info("no skin found for " + username);
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
		Logging.HTTPD.fine("query uuid of username " + username + ", response: " + responseText);

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
			Logging.HTTPD.fine("query profile of " + uuid + ", not found");
			return empty();
		}
		Logging.HTTPD.fine("query profile of " + uuid + ", response: " + responseText);

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
						.orElseThrow(() -> newUncheckedIOException("Invalid JSON: missing texture url")));
	}

}
