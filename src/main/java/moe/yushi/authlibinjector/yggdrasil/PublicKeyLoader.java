/*
 * Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package moe.yushi.authlibinjector.yggdrasil;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import moe.yushi.authlibinjector.APIMetadata;

/** Fetches keys only from the authentication server selected by the user. */
public final class PublicKeyLoader {

	public static final String FEATURE = "feature.enable_public_key_fetching";
	public static final int MAX_RESPONSE_BYTES = 128 * 1024;
	private static final int TIMEOUT_MILLIS = 10000;

	private PublicKeyLoader() {}

	public static boolean isEnabled(APIMetadata metadata) throws IOException {
		Object flag = metadata.getMeta().get(FEATURE);
		if (flag != null && !(flag instanceof Boolean)) {
			throw new IOException(FEATURE + " must be a boolean");
		}
		return Boolean.TRUE.equals(flag);
	}

	/** Validate configuration at startup without making a network request. */
	public static void validateEndpoint(String apiRoot) throws IOException {
		endpoint(apiRoot);
	}

	public static PublicKeys load(APIMetadata metadata) throws IOException {
		PublicKeys builtin = PublicKeys.mojang();
		if (!isEnabled(metadata)) {
			List<PublicKey> legacy = metadata.getDecodedPublickey()
					.map(Collections::singletonList).orElse(Collections.emptyList());
			return builtin.merge(new PublicKeys(legacy, legacy));
		}

		// In opt-in mode, only this endpoint supplies custom keys to a successful
		// snapshot. PublicKeyManager owns outage fallback separately.
		HttpURLConnection connection = (HttpURLConnection) endpoint(metadata.getApiRoot()).openConnection();
		connection.setConnectTimeout(TIMEOUT_MILLIS);
		connection.setReadTimeout(TIMEOUT_MILLIS);
		connection.setInstanceFollowRedirects(false);
		connection.setUseCaches(false);
		connection.setRequestProperty("Accept", "application/json");
		try {
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("Public key endpoint returned HTTP " + connection.getResponseCode());
			}
			if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) {
				throw new IOException("Public key response is too large");
			}
			try (InputStream in = connection.getInputStream()) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				int read;
				while ((read = in.read(buffer)) != -1) {
					if (out.size() + read > MAX_RESPONSE_BYTES) {
						throw new IOException("Public key response is too large");
					}
					out.write(buffer, 0, read);
				}
				return builtin.merge(PublicKeys.parse(new String(out.toByteArray(), UTF_8)));
			} catch (UncheckedIOException e) {
				throw new IOException("Invalid public key response", e.getCause());
			}
		} finally {
			connection.disconnect();
		}
	}

	private static URL endpoint(String apiRoot) throws IOException {
		try {
			URI root = new URI(apiRoot);
			String host = root.getHost();
			boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
					|| "[::1]".equals(host) || "::1".equals(host);
			if (host == null || root.getRawUserInfo() != null || root.getRawQuery() != null || root.getRawFragment() != null
					|| !("https".equalsIgnoreCase(root.getScheme()) || ("http".equalsIgnoreCase(root.getScheme()) && loopback))) {
				throw new IOException("Public key fetching requires an HTTPS API root (HTTP is allowed only on loopback)");
			}
			return new URL(apiRoot + (apiRoot.endsWith("/") ? "" : "/") + "minecraftservices/publickeys");
		} catch (URISyntaxException e) {
			throw new IOException("Invalid API root for public key fetching", e);
		}
	}
}
