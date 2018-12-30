package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.Logging;

public class URLProcessor {

	private static final Pattern URL_REGEX = Pattern.compile("^https?:\\/\\/(?<domain>[^\\/]+)(?<path>\\/.*)$");
	private static final Pattern LOCAL_URL_REGEX = Pattern.compile("^/(?<domain>[^\\/]+)(?<path>\\/.*)$");

	private List<URLFilter> filters;
	private URLRedirector redirector;

	public URLProcessor(List<URLFilter> filters, URLRedirector redirector) {
		this.filters = filters;
		this.redirector = redirector;
	}

	public Optional<String> transformURL(String inputUrl) {
		Matcher matcher = URL_REGEX.matcher(inputUrl);
		if (!matcher.find()) {
			return Optional.empty();
		}
		String domain = matcher.group("domain");
		String path = matcher.group("path");

		Optional<String> result = transform(domain, path);
		if (result.isPresent()) {
			Logging.TRANSFORM.fine("Transformed url [" + inputUrl + "] to [" + result.get() + "]");
		}
		return result;
	}

	private Optional<String> transform(String domain, String path) {
		boolean handleLocally = false;
		for (URLFilter filter : filters) {
			if (filter.canHandle(domain, path)) {
				handleLocally = true;
				break;
			}
		}

		if (handleLocally) {
			return Optional.of("http://127.0.0.1:" + getLocalApiPort() + "/" + domain + path);
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
					String domain = matcher.group("domain");
					String path = matcher.group("path");
					for (URLFilter filter : filters) {
						if (filter.canHandle(domain, path)) {
							Optional<Response> result;
							try {
								result = filter.handle(domain, path, session);
							} catch (Throwable e) {
								Logging.HTTPD.log(Level.WARNING, "An error occurred while processing request [" + session.getUri() + "]", e);
								return Response.newFixedLength(Status.INTERNAL_ERROR, null, null);
							}

							if (result.isPresent()) {
								Logging.HTTPD.fine("Request to [" + session.getUri() + "] is handled by [" + filter + "]");
								return result.get();
							}
						}
					}
				}

				Logging.HTTPD.fine("No handler is found for [" + session.getUri() + "]");
				return Response.newFixedLength(Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
			}
		};
	}
}
