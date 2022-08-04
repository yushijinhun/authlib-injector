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

import static java.nio.charset.StandardCharsets.UTF_8;
import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_JSON;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

/**
 * Intercepts Minecraft's request to https://api.minecraftservices.com/player/certificates,
 * and returns an empty response.
 */
public class ProfileKeyFilter implements URLFilter {

	@Override
	public boolean canHandle(String domain) {
		return domain.equals("api.minecraftservices.com");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (domain.equals("api.minecraftservices.com") && path.equals("/player/certificates") && session.getMethod().equals("POST")) {
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON, makeDummyResponse().toJSONString()));
		}
		return Optional.empty();
	}

	private JSONObject makeDummyResponse() {
		KeyPairGenerator generator;
		try {
			generator = KeyPairGenerator.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();

		Base64.Encoder base64 = Base64.getMimeEncoder(76, "\n".getBytes(UTF_8));
		String publicKeyPEM = "-----BEGIN RSA PUBLIC KEY-----\n" + base64.encodeToString(keyPair.getPublic().getEncoded()) + "\n-----END RSA PUBLIC KEY-----\n";
		String privateKeyPEM = "-----BEGIN RSA PRIVATE KEY-----\n" + base64.encodeToString(keyPair.getPrivate().getEncoded()) + "\n-----END RSA PRIVATE KEY-----\n";

		Instant now = Instant.now();
		Instant expiresAt = now.plus(48, ChronoUnit.HOURS);
		Instant refreshedAfter = now.plus(36, ChronoUnit.HOURS);

		JSONObject response = new JSONObject();
		JSONObject keyPairObj = new JSONObject();
		keyPairObj.put("privateKey", privateKeyPEM);
		keyPairObj.put("publicKey", publicKeyPEM);
		response.put("keyPair", keyPairObj);
		response.put("publicKeySignature", "AA==");
		response.put("publicKeySignatureV2", "AA==");
		response.put("expiresAt", DateTimeFormatter.ISO_INSTANT.format(expiresAt));
		response.put("refreshedAfter", DateTimeFormatter.ISO_INSTANT.format(refreshedAfter));
		return response;
	}

}
