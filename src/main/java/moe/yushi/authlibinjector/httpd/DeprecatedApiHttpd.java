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
import static moe.yushi.authlibinjector.util.JsonUtils.asArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asObject;
import static moe.yushi.authlibinjector.util.JsonUtils.asString;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;
import static moe.yushi.authlibinjector.util.LoggingUtils.debug;
import static moe.yushi.authlibinjector.util.LoggingUtils.info;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;

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
			info("[httpd] unable to fetch skin for {0}: {1}", username, e);
			return of(newFixedLengthResponse(Status.INTERNAL_ERROR, null, null));
		}

		if (skinUrl.isPresent()) {
			String url = skinUrl.get();
			debug("[httpd] retrieving skin for {0} from {1}", username, url);
			byte[] data;
			try {
				data = getURL(url);
			} catch (IOException e) {
				info("[httpd] unable to retrieve skin from {0}: {1}", url, e);
				return of(newFixedLengthResponse(Status.NOT_FOUND, null, null));
			}
			info("[httpd] retrieved skin for {0} from {1}, {2} bytes", username, url, data.length);
			return of(newFixedLengthResponse(Status.OK, "image/png", new ByteArrayInputStream(data), data.length));

		} else {
			info("[httpd] no skin found for {0}", username);
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
		debug("[httpd] query uuid of username {0}, response: {1}", username, responseText);

		JSONArray response = asArray(parseJson(responseText));
		if (response.size() == 0) {
			return empty();
		} else if (response.size() == 1) {
			JSONObject profile = asObject(response.get(0));
			return of(asString(profile.get("id")));
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
			debug("[httpd] query profile of {0}, not found", uuid);
			return empty();
		}
		debug("[httpd] query profile of {0}, response: {1}", uuid, responseText);

		JSONObject response = asObject(parseJson(responseText));
		return asArray(response.get("properties")).stream()
				.map(JsonUtils::asObject)
				.filter(property -> asString(property.get("name")).equals(propertyName))
				.findFirst()
				.map(property -> asString(property.get("value")));
	}

	private Optional<String> obtainTextureUrl(String texturesPayload, String textureType) throws UncheckedIOException {
		JSONObject payload = asObject(parseJson(texturesPayload));
		JSONObject textures = asObject(payload.get("textures"));

		return ofNullable(textures.get(textureType))
				.map(JsonUtils::asObject)
				.map(it -> ofNullable(it.get("url"))
						.map(JsonUtils::asString)
						.orElseThrow(() -> newUncheckedIOException("Invalid JSON: missing texture url")));
	}

}
