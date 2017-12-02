package org.to2mbn.authlibinjector.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.to2mbn.authlibinjector.util.IOUtils.asString;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class HttpRequester {

	/** Common http requester */
	public static final HttpRequester http = new HttpRequester();

	private int timeout = 15000;

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public String request(String method, String url) throws IOException {
		return request(method, url, null);
	}

	public String request(String method, String url, Map<String, String> headers) throws IOException {
		HttpURLConnection conn = createConnection(url, headers);
		conn.setRequestMethod(method);
		try {
			conn.connect();
			try (InputStream in = conn.getInputStream()) {
				return asString(in);
			}
		} catch (IOException e) {
			try (InputStream in = conn.getErrorStream()) {
				return readErrorStream(in, e);
			}
		} finally {
			conn.disconnect();
		}
	}

	public String requestWithPayload(String method, String url, Object payload, String contentType) throws IOException {
		return requestWithPayload(method, url, payload, contentType, null);
	}

	public String requestWithPayload(String method, String url, Object payload, String contentType, Map<String, String> headers) throws IOException {
		byte[] bytePayload;
		if (payload instanceof byte[]) {
			bytePayload = (byte[]) payload;
		} else if (payload == null) {
			bytePayload = new byte[0];
		} else {
			bytePayload = String.valueOf(payload).getBytes(UTF_8);
		}

		HttpURLConnection conn = createConnection(url, headers);
		conn.setRequestMethod(method);
		conn.setRequestProperty("Content-Type", contentType);
		conn.setRequestProperty("Content-Length", String.valueOf(bytePayload.length));
		conn.setDoOutput(true);

		try {
			conn.connect();
			try (OutputStream out = conn.getOutputStream()) {
				out.write(bytePayload);
			}
			try (InputStream in = conn.getInputStream()) {
				return asString(in);
			}
		} catch (IOException e) {
			try (InputStream in = conn.getErrorStream()) {
				return readErrorStream(in, e);
			}
		} finally {
			conn.disconnect();
		}
	}

	private String readErrorStream(InputStream in, IOException e) throws IOException {
		if (in == null)
			throw e;

		try {
			return asString(in);
		} catch (IOException e1) {
			if (e != e1)
				e1.addSuppressed(e);

			throw e1;
		}
	}

	private HttpURLConnection createConnection(String url, Map<String, String> headers) throws IOException {
		HttpURLConnection conn = createConnection(new URL(url));
		if (headers != null)
			headers.forEach((key, value) -> conn.setRequestProperty(key, value));
		return conn;
	}

	private HttpURLConnection createConnection(URL url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(timeout);
		conn.setReadTimeout(timeout);
		conn.setUseCaches(false);
		return conn;
	}

}
