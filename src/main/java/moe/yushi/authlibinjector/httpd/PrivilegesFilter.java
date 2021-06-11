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
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

public class PrivilegesFilter implements URLFilter {

	private final Map<String, Boolean> privileges = new LinkedHashMap<>();

	public PrivilegesFilter() {
		privileges.put("onlineChat", true);
		privileges.put("multiplayerServer", true);
		privileges.put("multiplayerRealms", true);
		privileges.put("telemetry", false);
	}

	@Override
	public boolean canHandle(String domain) {
		return domain.equals("api.minecraftservices.com");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (domain.equals("api.minecraftservices.com") && path.equals("/privileges") && session.getMethod().equals("GET")) {
			JSONObject response = new JSONObject();
			JSONObject privilegesJson = new JSONObject();
			privileges.forEach((name, enabled) -> {
				JSONObject privilegeJson = new JSONObject();
				privilegeJson.put("enabled", enabled);
				privilegesJson.put(name, privilegeJson);
			});
			response.put("privileges", privilegesJson);
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, response.toJSONString()));
		} else {
			return Optional.empty();
		}
	}
}
