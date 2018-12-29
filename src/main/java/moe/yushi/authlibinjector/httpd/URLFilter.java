package moe.yushi.authlibinjector.httpd;

import java.util.Optional;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public interface URLFilter {

	boolean canHandle(String domain, String path);

	Optional<Response> handle(String domain, String path, IHTTPSession session);
}
