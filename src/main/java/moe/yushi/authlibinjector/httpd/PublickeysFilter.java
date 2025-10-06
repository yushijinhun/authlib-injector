/*
 * Copyright (C) 2023  Haowei Wen <yushijinhun@gmail.com> and contributors
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
import java.security.PublicKey;
import java.util.Base64;
import java.util.Optional;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.transform.support.YggdrasilKeyTransformUnit;

public class PublickeysFilter implements URLFilter {

	@Override
	public boolean canHandle(String domain) {
		return domain.equals("api.minecraftservices.com");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (domain.equals("api.minecraftservices.com") && path.equals("/publickeys") && session.getMethod().equals("GET")) {
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, makePublickeysResponse().toJSONString()));
		}
		return Optional.empty();
	}

	private JSONObject makePublickeysResponse() {
		JSONObject response = new JSONObject();
		JSONArray profilePropertyKeys = new JSONArray();
		JSONArray playerCertificateKeys = new JSONArray();

		for (PublicKey key : YggdrasilKeyTransformUnit.PROFILE_PROPERTY_PUBLIC_KEYS) {
			JSONObject entry = new JSONObject();
			entry.put("publicKey", Base64.getEncoder().encodeToString(key.getEncoded()));
			profilePropertyKeys.add(entry);
		}

		for (PublicKey key : YggdrasilKeyTransformUnit.PLAYER_CERTIFICATE_PUBLIC_KEYS) {
			JSONObject entry = new JSONObject();
			entry.put("publicKey", Base64.getEncoder().encodeToString(key.getEncoded()));
			playerCertificateKeys.add(entry);
		}

		response.put("profilePropertyKeys", profilePropertyKeys);
		response.put("playerCertificateKeys", playerCertificateKeys);
		return response;
	}
}
