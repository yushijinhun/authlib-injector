package moe.yushi.authlibinjector.httpd;

import java.util.Optional;

public interface URLRedirector {
	Optional<String> redirect(String domain, String path);
}
