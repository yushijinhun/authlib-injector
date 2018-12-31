package moe.yushi.authlibinjector.httpd;

import java.util.Optional;

/**
 * A URLRedirector modifies the URLs found in the bytecode,
 * and points them to the customized authentication server.
 */
public interface URLRedirector {
	Optional<String> redirect(String domain, String path);
}
