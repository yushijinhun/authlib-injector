/*
 * Copyright (C) 2020  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

public final class IOUtils {

	public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

	private static HttpURLConnection createConnection(String url, Proxy proxy) throws IOException {
		if (proxy == null) {
			return (HttpURLConnection) new URL(url).openConnection();
		} else {
			return (HttpURLConnection) new URL(url).openConnection(proxy);
		}
	}

	public static byte[] http(String method, String url) throws IOException {
		return http(method, url, null);
	}

	public static byte[] http(String method, String url, Proxy proxy) throws IOException {
		HttpURLConnection conn = createConnection(url, proxy);
		conn.setRequestMethod(method);
		try (InputStream in = conn.getInputStream()) {
			return asBytes(in);
		}
	}

	public static byte[] http(String method, String url, byte[] payload, String contentType) throws IOException {
		return http(method, url, payload, contentType, null);
	}

	public static byte[] http(String method, String url, byte[] payload, String contentType, Proxy proxy) throws IOException {
		HttpURLConnection conn = createConnection(url, proxy);
		conn.setRequestMethod(method);
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", contentType);
		try (OutputStream out = conn.getOutputStream()) {
			out.write(payload);
		}
		try (InputStream in = conn.getInputStream()) {
			return asBytes(in);
		}
	}

	public static byte[] asBytes(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		transfer(in, out);
		return out.toByteArray();
	}

	public static void transfer(InputStream from, OutputStream to) throws IOException {
		byte[] buf = new byte[8192];
		int read;
		while ((read = from.read(buf)) != -1) {
			to.write(buf, 0, read);
		}
	}

	public static String asString(byte[] bytes) {
		return new String(bytes, UTF_8);
	}

	public static String removeNewLines(String input) {
		return input.replace("\n", "")
				.replace("\r", "");
	}

	public static UncheckedIOException newUncheckedIOException(String message) throws UncheckedIOException {
		return new UncheckedIOException(new IOException(message));
	}

	public static UncheckedIOException newUncheckedIOException(String message, Throwable cause) throws UncheckedIOException {
		return new UncheckedIOException(new IOException(message, cause));
	}

	private IOUtils() {}

}
