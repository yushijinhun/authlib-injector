/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import java.io.IOException;
import java.util.Optional;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;

/**
 * A URLFilter filters the URLs in the bytecode, and intercepts those he is interested in.
 */
public interface URLFilter {

	/**
	 * Returns true if the filter MAY be interested in the given URL.
	 *
	 * The URL is grabbed from the bytecode, and it may be different from the URL being used at runtime.
	 * Therefore, the URLs may be incomplete or malformed, or contain some template symbols. eg:
	 * https://api.mojang.com/profiles/ (the actual one being used is https://api.mojang.com/profiles/minecraft)
	 * https://sessionserver.mojang.com/session/minecraft/profile/<uuid> (template symbols)
	 *
	 * If this method returns true for the given URL, the URL will be intercepted.
	 * And when a request is sent to this URL, handle() will be invoked.
	 * If it turns out that the filter doesn't really want to intercept the URL (handle() returns empty),
	 * the request will be reverse-proxied to the original URL, as if nothing happened.
	 */
	boolean canHandle(String domain, String path);

	Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException;
}
