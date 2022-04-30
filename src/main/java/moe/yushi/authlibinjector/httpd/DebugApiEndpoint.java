/*
 * Copyright (C) 2022  Haowei Wen <yushijinhun@gmail.com> and contributors
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
import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.transform.PerformanceMetrics;

/**
 * Authlib-injector's debug API
 */
public class DebugApiEndpoint {

	public Response serve(IHTTPSession session) {
		if (session.getUri().equals("/debug/metrics") && session.getMethod().equals("GET")) {
			PerformanceMetrics metrics = AuthlibInjector.getClassTransformer().performanceMetrics;
			JSONObject response = new JSONObject();
			response.put("classesProcessed", metrics.getClassesProcessed());
			response.put("totalTime", metrics.getTotalTime());
			return Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, response.toJSONString());
		} else {
			return Response.newFixedLength(Status.NOT_FOUND, null, null);
		}
	}
}
