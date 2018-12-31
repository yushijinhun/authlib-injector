package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Handles one session, i.e. parses the HTTP request and returns the
 * response.
 */
public interface IHTTPSession {

	void execute() throws IOException;

	Map<String, String> getHeaders();

	InputStream getInputStream() throws IOException;

	String getMethod();

	Map<String, List<String>> getParameters();

	String getQueryParameterString();

	/**
	 * @return the path part of the URL.
	 */
	String getUri();

	/**
	 * Get the remote ip address of the requester.
	 *
	 * @return the IP address.
	 */
	String getRemoteIpAddress();

	/**
	 * Get the remote hostname of the requester.
	 *
	 * @return the hostname.
	 */
	String getRemoteHostName();
}
