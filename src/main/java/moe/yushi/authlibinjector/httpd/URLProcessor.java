package moe.yushi.authlibinjector.httpd;

import static moe.yushi.authlibinjector.util.IOUtils.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IStatus;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.Logging;

public class URLProcessor {

	private static final Pattern URL_REGEX = Pattern.compile("^(?<protocol>https?):\\/\\/(?<domain>[^\\/]+)(?<path>\\/.*)$");
	private static final Pattern LOCAL_URL_REGEX = Pattern.compile("^/(?<protocol>https?)/(?<domain>[^\\/]+)(?<path>\\/.*)$");

	private List<URLFilter> filters;
	private URLRedirector redirector;

	public URLProcessor(List<URLFilter> filters, URLRedirector redirector) {
		this.filters = filters;
		this.redirector = redirector;
	}

	/**
	 * Transforms the input URL(which is grabbed from the bytecode).
	 *
	 * If any filter is interested in the URL, the URL will be redirected to the local HTTP server.
	 * Otherwise, the URLRedirector will be invoked to determine whether the URL should be modified
	 * and pointed to the customized authentication server.
	 * If none of above happens, empty is returned.
	 *
	 * @return the transformed URL, or empty if it doesn't need to be transformed
	 */
	public Optional<String> transformURL(String inputUrl) {
		Matcher matcher = URL_REGEX.matcher(inputUrl);
		if (!matcher.find()) {
			return Optional.empty();
		}
		String protocol = matcher.group("protocol");
		String domain = matcher.group("domain");
		String path = matcher.group("path");

		Optional<String> result = transform(protocol, domain, path);
		if (result.isPresent()) {
			Logging.TRANSFORM.fine("Transformed url [" + inputUrl + "] to [" + result.get() + "]");
		}
		return result;
	}

	private Optional<String> transform(String protocol, String domain, String path) {
		boolean handleLocally = false;
		for (URLFilter filter : filters) {
			if (filter.canHandle(domain, path)) {
				handleLocally = true;
				break;
			}
		}

		if (handleLocally) {
			return Optional.of("http://127.0.0.1:" + getLocalApiPort() + "/" + protocol + "/" + domain + path);
		}

		return redirector.redirect(domain, path);
	}

	private volatile NanoHTTPD httpd;
	private final Object httpdLock = new Object();

	private int getLocalApiPort() {
		synchronized (httpdLock) {
			if (httpd == null) {
				httpd = createHttpd();
				try {
					httpd.start();
				} catch (IOException e) {
					throw new IllegalStateException("Httpd failed to start");
				}
				Logging.HTTPD.info("Httpd is running on port " + httpd.getListeningPort());
			}
			return httpd.getListeningPort();
		}
	}

	private NanoHTTPD createHttpd() {
		return new NanoHTTPD("127.0.0.1", 0) {
			@Override
			public Response serve(IHTTPSession session) {
				Matcher matcher = LOCAL_URL_REGEX.matcher(session.getUri());
				if (matcher.find()) {
					String protocol = matcher.group("protocol");
					String domain = matcher.group("domain");
					String path = matcher.group("path");
					for (URLFilter filter : filters) {
						if (filter.canHandle(domain, path)) {
							Optional<Response> result;
							try {
								result = filter.handle(domain, path, session);
							} catch (Throwable e) {
								Logging.HTTPD.log(Level.WARNING, "An error occurred while processing request [" + session.getUri() + "]", e);
								return Response.newFixedLength(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error");
							}

							if (result.isPresent()) {
								Logging.HTTPD.fine("Request to [" + session.getUri() + "] is handled by [" + filter + "]");
								return result.get();
							}
						}
					}

					String target = redirector.redirect(domain, path)
							.orElseGet(() -> protocol + "://" + domain + path);
					try {
						return reverseProxy(session, target);
					} catch (IOException e) {
						Logging.HTTPD.log(Level.WARNING, "Reverse proxy error", e);
						return Response.newFixedLength(Status.BAD_GATEWAY, MIME_PLAINTEXT, "Bad Gateway");
					}
				} else {
					Logging.HTTPD.fine("No handler is found for [" + session.getUri() + "]");
					return Response.newFixedLength(Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
				}
			}
		};
	}

	private static final Set<String> ignoredHeaders = new HashSet<>(Arrays.asList("host", "expect", "connection", "keep-alive", "transfer-encoding"));

	@SuppressWarnings("resource")
	private Response reverseProxy(IHTTPSession session, String upstream) throws IOException {
		String method = session.getMethod();

		String url = session.getQueryParameterString() == null ? upstream : upstream + "?" + session.getQueryParameterString();

		Map<String, String> requestHeaders = session.getHeaders();
		ignoredHeaders.forEach(requestHeaders::remove);

		InputStream clientIn = session.getInputStream();

		Logging.HTTPD.fine(() -> "Reverse proxy: > " + method + " " + url + ", headers: " + requestHeaders);

		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod(method);
		conn.setDoOutput(clientIn != null);
		requestHeaders.forEach(conn::setRequestProperty);

		if (clientIn != null) {
			try (OutputStream upstreamOut = conn.getOutputStream()) {
				transfer(clientIn, upstreamOut);
			}
		}

		int responseCode = conn.getResponseCode();
		String reponseMessage = conn.getResponseMessage();
		Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
		conn.getHeaderFields().forEach((name, values) -> {
			if (name != null && !ignoredHeaders.contains(name.toLowerCase())) {
				responseHeaders.put(name, values);
			}
		});
		InputStream upstreamIn;
		try {
			upstreamIn = conn.getInputStream();
		} catch (IOException e) {
			upstreamIn = conn.getErrorStream();
		}
		Logging.HTTPD.fine(() -> "Reverse proxy: < " + responseCode + " " + reponseMessage + " , headers: " + responseHeaders);

		IStatus status = new IStatus() {
			@Override
			public int getRequestStatus() {
				return responseCode;
			}

			@Override
			public String getDescription() {
				return responseCode + " " + reponseMessage;
			}
		};

		long contentLength = -1;
		for (Entry<String, List<String>> header : responseHeaders.entrySet()) {
			if ("content-length".equalsIgnoreCase(header.getKey())) {
				contentLength = Long.parseLong(header.getValue().get(0));
				break;
			}
		}
		Response response;
		if (contentLength != -1) {
			response = Response.newFixedLength(status, null, upstreamIn, contentLength);
		} else {
			response = Response.newChunked(status, null, upstreamIn);
		}
		responseHeaders.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));

		return response;
	}
}
