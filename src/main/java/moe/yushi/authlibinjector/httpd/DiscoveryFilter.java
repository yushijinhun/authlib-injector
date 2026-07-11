/*
 * Copyright (C) 2026  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector.httpd;

import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_JSON;
import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static moe.yushi.authlibinjector.util.Logging.log;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

public class DiscoveryFilter implements URLFilter {

	public static final JSONObject MOJANG_DISCOVERY_JSON;

	private final ConcurrentHashMap<URLProcessor, JSONObject> transformedDiscoveryJSONs = new ConcurrentHashMap<>();

	static {
		// Hardcode current response from discovery endpoint in case Mojang
		// moves an endpoint to somewhere the authlib-injector filters don't
		// recognize
		try (InputStream in = DiscoveryFilter.class.getResourceAsStream("/discovery_minecraftservices_com_minecraft_client.json")) {
			MOJANG_DISCOVERY_JSON = asJsonObject(parseJson(asString(asBytes(in))));
		} catch (IOException | UncheckedIOException e) {
			throw new RuntimeException("Failed to load hardcoded Mojang discovery response", e);
		}
	}

	@Override
	public boolean canHandle(String domain) {
		return domain.equals("discovery.minecraftservices.com");
	}

	@Override
	public Optional<Response> handle(URLProcessor urlProcessor, String domain, String path, IHTTPSession session) throws IOException {
		if (domain.equals("discovery.minecraftservices.com") && path.equals("/minecraft/client") && session.getMethod().equals("GET")) {
		JSONObject response = transformedDiscoveryJSONs.computeIfAbsent(urlProcessor, p -> {
			JSONObject transformed = (JSONObject) transformDiscoveryJSON(MOJANG_DISCOVERY_JSON, p);
			log(DEBUG, "Transformed discovery JSON: " + transformed.toJSONString());
			return transformed;
		});
		return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, response.toJSONString()));
		}
		return Optional.empty();
	}

	// Returns a deep copy of the given JSON with every string value run through
	// urlProcessor.transformURL, so any endpoint URIs are pointed at the local server.
	private static Object transformDiscoveryJSON(Object json, URLProcessor urlProcessor) {
		if (json instanceof JSONObject) {
			JSONObject copy = new JSONObject();
			((JSONObject) json).forEach((key, value) -> copy.put(key, transformDiscoveryJSON(value, urlProcessor)));
			return copy;
		} else if (json instanceof JSONArray) {
			JSONArray copy = new JSONArray();
			for (Object element : (JSONArray) json) {
				copy.add(transformDiscoveryJSON(element, urlProcessor));
			}
			return copy;
		} else if (json instanceof String) {
			return urlProcessor.transformURL((String) json).orElse((String) json);
		} else {
			return json;
		}
	}
}
