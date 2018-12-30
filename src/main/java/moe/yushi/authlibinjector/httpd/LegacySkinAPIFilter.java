package moe.yushi.authlibinjector.httpd;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.getURL;
import static moe.yushi.authlibinjector.util.IOUtils.newUncheckedIOException;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;

public class LegacySkinAPIFilter implements URLFilter {

	private static final Pattern PATH_SKINS = Pattern.compile("^/MinecraftSkins/(?<username>[^/]+)\\.png$");

	private YggdrasilClient upstream;

	public LegacySkinAPIFilter(YggdrasilClient upstream) {
		this.upstream = upstream;
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
			skinUrl = upstream.queryUUID(username)
					.flatMap(uuid -> upstream.queryProfile(uuid, false))
					.flatMap(profile -> Optional.ofNullable(profile.properties.get("textures")))
					.map(property -> asString(Base64.getDecoder().decode(property.value)))
					.flatMap(texturesPayload -> obtainTextureUrl(texturesPayload, "SKIN"));
		} catch (UncheckedIOException e) {
			throw newUncheckedIOException("Failed to fetch skin metadata for " + username, e);
		}

		if (skinUrl.isPresent()) {
			String url = skinUrl.get();
			Logging.HTTPD.fine("Retrieving skin for " + username + " from " + url);
			byte[] data;
			try {
				data = getURL(url);
			} catch (IOException e) {
				throw newUncheckedIOException("Failed to retrieve skin from " + url, e);
			}
			Logging.HTTPD.info("Retrieved skin for " + username + " from " + url + ", " + data.length + " bytes");
			return of(Response.newFixedLength(Status.OK, "image/png", new ByteArrayInputStream(data), data.length));

		} else {
			Logging.HTTPD.info("No skin is found for " + username);
			return of(Response.newFixedLength(Status.NOT_FOUND, null, null));
		}
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
