/*
 * Copyright (C) 2021  Haowei Wen <yushijinhun@gmail.com> and contributors
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
import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_TEXT;
import java.io.IOException;
import java.util.Optional;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;

/**
 * Disables Mojang's anti-features.
 */
public class AntiFeaturesFilter implements URLFilter {

	private static final String RESPONSE_PRIVILEGES = "{\"privileges\":{\"onlineChat\":{\"enabled\":true},\"multiplayerServer\":{\"enabled\":true},\"multiplayerRealms\":{\"enabled\":true},\"telemetry\":{\"enabled\":false}}}";
	private static final String RESPONSE_PLAYER_ATTRIBUTES = "{\"privileges\":{\"multiplayerRealms\":{\"enabled\":true},\"multiplayerServer\":{\"enabled\":true},\"onlineChat\":{\"enabled\":true},\"telemetry\":{\"enabled\":false}},\"profanityFilterPreferences\":{\"profanityFilterOn\":false}}";
	private static final String RESPONSE_PRIVACY_BLOCKLIST = "{\"blockedProfiles\":[]}";

	@Override
	public boolean canHandle(String domain) {
		return domain.equals("api.minecraftservices.com") || domain.equals("sessionserver.mojang.com");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (domain.equals("api.minecraftservices.com") && path.equals("/privileges") && session.getMethod().equals("GET")) {
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, RESPONSE_PRIVILEGES));
		} else if (domain.equals("api.minecraftservices.com") && path.equals("/player/attributes") && session.getMethod().equals("GET")) {
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, RESPONSE_PLAYER_ATTRIBUTES));
		} else if (domain.equals("api.minecraftservices.com") && path.equals("/privacy/blocklist") && session.getMethod().equals("GET")) {
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, RESPONSE_PRIVACY_BLOCKLIST));
		} else if (domain.equals("sessionserver.mojang.com") && path.equals("/blockedservers") && session.getMethod().equals("GET")) {
			return Optional.of(Response.newFixedLength(Status.NOT_FOUND, CONTENT_TYPE_TEXT, ""));
		} else {
			return Optional.empty();
		}
	}
}
