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

import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_TEXT;
import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;

/**
 * Proxies Floodgate Global Linking query requests to the custom authentication server.
 */
public class FloodgateGlobalLinkingFilter implements URLFilter {

	private static final String GEYSER_GLOBAL_API_DOMAIN = "api.geysermc.org";
	private static final Pattern LINK_QUERY_PATH = Pattern.compile("^/v2/link/(bedrock|java)/[^/]+$");

	private String apiRoot;

	public FloodgateGlobalLinkingFilter(String apiRoot) {
		this.apiRoot = apiRoot.endsWith("/") ? apiRoot : apiRoot + "/";
	}

	@Override
	public boolean canHandle(String domain) {
		return domain.equals(GEYSER_GLOBAL_API_DOMAIN);
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (!domain.equals(GEYSER_GLOBAL_API_DOMAIN) || !session.getMethod().equals("GET") || !LINK_QUERY_PATH.matcher(path).find()) {
			return Optional.empty();
		}

		String target = apiRoot + "geyser" + path;

		try {
			return Optional.of(URLProcessor.reverseProxy(session, target));
		} catch (IOException e) {
			log(WARNING, "Failed to proxy Floodgate Global Linking request to [" + target + "]", e);
			return Optional.of(Response.newFixedLength(Status.BAD_GATEWAY, CONTENT_TYPE_TEXT, "Bad Gateway"));
		}
	}
}
