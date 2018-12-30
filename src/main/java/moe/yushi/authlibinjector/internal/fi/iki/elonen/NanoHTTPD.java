package moe.yushi.authlibinjector.internal.fi.iki.elonen;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2015 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD.Response.IStatus;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD.Response.Status;

/**
 * A simple, tiny, nicely embeddable HTTP server in Java
 * <p/>
 * <p/>
 * NanoHTTPD
 * <p>
 * Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen,
 * 2010 by Konstantinos Togias
 * </p>
 * <p/>
 * <p/>
 * <b>Features + limitations: </b>
 * <ul>
 * <p/>
 * <li>Only one Java file</li>
 * <li>Java 5 compatible</li>
 * <li>Released as open source, Modified BSD licence</li>
 * <li>No fixed config files, logging, authorization etc. (Implement yourself if
 * you need them.)</li>
 * <li>Supports parameter parsing of GET and POST methods (+ rudimentary PUT
 * support in 1.25)</li>
 * <li>Supports both dynamic content and file serving</li>
 * <li>Supports file upload (since version 1.2, 2010)</li>
 * <li>Supports partial content (streaming)</li>
 * <li>Supports ETags</li>
 * <li>Never caches anything</li>
 * <li>Doesn't limit bandwidth, request time or simultaneous connections</li>
 * <li>Default code serves files and shows all HTTP parameters and headers</li>
 * <li>File server supports directory listing, index.html and index.htm</li>
 * <li>File server supports partial content (streaming)</li>
 * <li>File server supports ETags</li>
 * <li>File server does the 301 redirection trick for directories without '/'</li>
 * <li>File server supports simple skipping for files (continue download)</li>
 * <li>File server serves also very long files without memory overhead</li>
 * <li>Contains a built-in list of most common MIME types</li>
 * <li>All header names are converted to lower case so they don't vary between
 * browsers/clients</li>
 * <p/>
 * </ul>
 * <p/>
 * <p/>
 * <b>How to use: </b>
 * <ul>
 * <p/>
 * <li>Subclass and implement serve() and embed to your own program</li>
 * <p/>
 * </ul>
 * <p/>
 * See the separate "LICENSE.md" file for the distribution license (Modified BSD
 * licence)
 */
public abstract class NanoHTTPD {

	/**
	 * Pluggable strategy for asynchronously executing requests.
	 */
	public interface AsyncRunner {

		void closeAll();

		void closed(ClientHandler clientHandler);

		void exec(ClientHandler code);
	}

	/**
	 * The runnable that will be used for every new client connection.
	 */
	public class ClientHandler implements Runnable {

		private final InputStream inputStream;

		private final Socket acceptSocket;

		public ClientHandler(InputStream inputStream, Socket acceptSocket) {
			this.inputStream = inputStream;
			this.acceptSocket = acceptSocket;
		}

		public void close() {
			safeClose(this.inputStream);
			safeClose(this.acceptSocket);
		}

		@Override
		public void run() {
			OutputStream outputStream = null;
			try {
				outputStream = this.acceptSocket.getOutputStream();
				HTTPSession session = new HTTPSession(this.inputStream, outputStream, this.acceptSocket.getInetAddress());
				while (!this.acceptSocket.isClosed()) {
					session.execute();
				}
			} catch (Exception e) {
				// When the socket is closed by the client,
				// we throw our own SocketException
				// to break the "keep alive" loop above. If
				// the exception was anything other
				// than the expected SocketException OR a
				// SocketTimeoutException, print the
				// stacktrace
				if (!(e instanceof SocketException && "NanoHttpd Shutdown".equals(e.getMessage())) && !(e instanceof SocketTimeoutException)) {
					NanoHTTPD.LOG.log(Level.SEVERE, "Communication with the client broken, or an bug in the handler code", e);
				}
			} finally {
				safeClose(outputStream);
				safeClose(this.inputStream);
				safeClose(this.acceptSocket);
				NanoHTTPD.this.asyncRunner.closed(this);
			}
		}
	}

	/**
	 * Default threading strategy for NanoHTTPD.
	 * <p/>
	 * <p>
	 * By default, the server spawns a new Thread for every incoming request.
	 * These are set to <i>daemon</i> status, and named according to the request
	 * number. The name is useful when profiling the application.
	 * </p>
	 */
	public static class DefaultAsyncRunner implements AsyncRunner {

		private long requestCount;

		private final List<ClientHandler> running = Collections.synchronizedList(new ArrayList<NanoHTTPD.ClientHandler>());

		/**
		 * @return a list with currently running clients.
		 */
		public List<ClientHandler> getRunning() {
			return running;
		}

		@Override
		public void closeAll() {
			// copy of the list for concurrency
			for (ClientHandler clientHandler : new ArrayList<>(this.running)) {
				clientHandler.close();
			}
		}

		@Override
		public void closed(ClientHandler clientHandler) {
			this.running.remove(clientHandler);
		}

		@Override
		public void exec(ClientHandler clientHandler) {
			++this.requestCount;
			Thread t = new Thread(clientHandler);
			t.setDaemon(true);
			t.setName("NanoHttpd Request Processor (#" + this.requestCount + ")");
			this.running.add(clientHandler);
			t.start();
		}
	}

	/**
	 * Creates a normal ServerSocket for TCP connections
	 */
	public static class DefaultServerSocketFactory implements ServerSocketFactory {

		@Override
		public ServerSocket create() throws IOException {
			return new ServerSocket();
		}

	}

	protected static class ContentType {

		private static final String ASCII_ENCODING = "US-ASCII";

		private static final String MULTIPART_FORM_DATA_HEADER = "multipart/form-data";

		private static final String CONTENT_REGEX = "[ |\t]*([^/^ ^;^,]+/[^ ^;^,]+)";

		private static final Pattern MIME_PATTERN = Pattern.compile(CONTENT_REGEX, Pattern.CASE_INSENSITIVE);

		private static final String CHARSET_REGEX = "[ |\t]*(charset)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?";

		private static final Pattern CHARSET_PATTERN = Pattern.compile(CHARSET_REGEX, Pattern.CASE_INSENSITIVE);

		private static final String BOUNDARY_REGEX = "[ |\t]*(boundary)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?";

		private static final Pattern BOUNDARY_PATTERN = Pattern.compile(BOUNDARY_REGEX, Pattern.CASE_INSENSITIVE);

		private final String contentTypeHeader;

		private final String contentType;

		private final String encoding;

		private final String boundary;

		public ContentType(String contentTypeHeader) {
			this.contentTypeHeader = contentTypeHeader;
			if (contentTypeHeader != null) {
				contentType = getDetailFromContentHeader(contentTypeHeader, MIME_PATTERN, "", 1);
				encoding = getDetailFromContentHeader(contentTypeHeader, CHARSET_PATTERN, null, 2);
			} else {
				contentType = "";
				encoding = "UTF-8";
			}
			if (MULTIPART_FORM_DATA_HEADER.equalsIgnoreCase(contentType)) {
				boundary = getDetailFromContentHeader(contentTypeHeader, BOUNDARY_PATTERN, null, 2);
			} else {
				boundary = null;
			}
		}

		private String getDetailFromContentHeader(String contentTypeHeader, Pattern pattern, String defaultValue, int group) {
			Matcher matcher = pattern.matcher(contentTypeHeader);
			return matcher.find() ? matcher.group(group) : defaultValue;
		}

		public String getContentTypeHeader() {
			return contentTypeHeader;
		}

		public String getContentType() {
			return contentType;
		}

		public String getEncoding() {
			return encoding == null ? ASCII_ENCODING : encoding;
		}

		public String getBoundary() {
			return boundary;
		}

		public boolean isMultipart() {
			return MULTIPART_FORM_DATA_HEADER.equalsIgnoreCase(contentType);
		}

		public ContentType tryUTF8() {
			if (encoding == null) {
				return new ContentType(this.contentTypeHeader + "; charset=UTF-8");
			}
			return this;
		}
	}

	protected class HTTPSession implements IHTTPSession {

		public static final int BUFSIZE = 8192;

		public static final int MAX_HEADER_SIZE = 1024;

		private final OutputStream outputStream;

		private final BufferedInputStream inputStream;

		private int splitbyte;

		private int rlen;

		private String uri;

		private String method;

		private Map<String, List<String>> parms;

		private Map<String, String> headers;

		private String queryParameterString;

		private String remoteIp;

		private String remoteHostname;

		private String protocolVersion;

		public HTTPSession(InputStream inputStream, OutputStream outputStream) {
			this.inputStream = new BufferedInputStream(inputStream, HTTPSession.BUFSIZE);
			this.outputStream = outputStream;
		}

		public HTTPSession(InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
			this.inputStream = new BufferedInputStream(inputStream, HTTPSession.BUFSIZE);
			this.outputStream = outputStream;
			this.remoteIp = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "127.0.0.1" : inetAddress.getHostAddress().toString();
			this.remoteHostname = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "localhost" : inetAddress.getHostName().toString();
			this.headers = new HashMap<>();
		}

		/**
		 * Decodes the sent headers and loads the data into Key/value pairs
		 */
		private void decodeHeader(BufferedReader in, Map<String, String> pre, Map<String, List<String>> parms, Map<String, String> headers) throws ResponseException {
			try {
				// Read the request line
				String inLine = in.readLine();
				if (inLine == null) {
					return;
				}

				StringTokenizer st = new StringTokenizer(inLine);
				if (!st.hasMoreTokens()) {
					throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
				}

				pre.put("method", st.nextToken());

				if (!st.hasMoreTokens()) {
					throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
				}

				String uri = st.nextToken();

				// Decode parameters from the URI
				int qmi = uri.indexOf('?');
				if (qmi >= 0) {
					decodeParms(uri.substring(qmi + 1), parms);
					uri = decodePercent(uri.substring(0, qmi));
				} else {
					uri = decodePercent(uri);
				}

				// If there's another token, its protocol version,
				// followed by HTTP headers.
				// NOTE: this now forces header names lower case since they are
				// case insensitive and vary by client.
				if (st.hasMoreTokens()) {
					protocolVersion = st.nextToken();
				} else {
					protocolVersion = "HTTP/1.1";
					NanoHTTPD.LOG.log(Level.FINE, "no protocol version specified, strange. Assuming HTTP/1.1.");
				}
				String line = in.readLine();
				while (line != null && !line.trim().isEmpty()) {
					int p = line.indexOf(':');
					if (p >= 0) {
						headers.put(line.substring(0, p).trim().toLowerCase(Locale.US), line.substring(p + 1).trim());
					}
					line = in.readLine();
				}

				pre.put("uri", uri);
			} catch (IOException ioe) {
				throw new ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
			}
		}

		/**
		 * Decodes parameters in percent-encoded URI-format ( e.g.
		 * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
		 * Map.
		 */
		private void decodeParms(String parms, Map<String, List<String>> p) {
			if (parms == null) {
				this.queryParameterString = "";
				return;
			}

			this.queryParameterString = parms;
			StringTokenizer st = new StringTokenizer(parms, "&");
			while (st.hasMoreTokens()) {
				String e = st.nextToken();
				int sep = e.indexOf('=');
				String key = null;
				String value = null;

				if (sep >= 0) {
					key = decodePercent(e.substring(0, sep)).trim();
					value = decodePercent(e.substring(sep + 1));
				} else {
					key = decodePercent(e).trim();
					value = "";
				}

				List<String> values = p.get(key);
				if (values == null) {
					values = new ArrayList<>();
					p.put(key, values);
				}

				values.add(value);
			}
		}

		@SuppressWarnings("resource")
		@Override
		public void execute() throws IOException {
			Response r = null;
			try {
				// Read the first 8192 bytes.
				// The full header should fit in here.
				// Apache's default header limit is 8KB.
				// Do NOT assume that a single read will get the entire header
				// at once!
				byte[] buf = new byte[HTTPSession.BUFSIZE];
				this.splitbyte = 0;
				this.rlen = 0;

				int read = -1;
				this.inputStream.mark(HTTPSession.BUFSIZE);
				try {
					read = this.inputStream.read(buf, 0, HTTPSession.BUFSIZE);
				} catch (IOException e) {
					safeClose(this.inputStream);
					safeClose(this.outputStream);
					throw new SocketException("NanoHttpd Shutdown");
				}
				if (read == -1) {
					// socket was been closed
					safeClose(this.inputStream);
					safeClose(this.outputStream);
					throw new SocketException("NanoHttpd Shutdown");
				}
				while (read > 0) {
					this.rlen += read;
					this.splitbyte = findHeaderEnd(buf, this.rlen);
					if (this.splitbyte > 0) {
						break;
					}
					read = this.inputStream.read(buf, this.rlen, HTTPSession.BUFSIZE - this.rlen);
				}

				if (this.splitbyte < this.rlen) {
					this.inputStream.reset();
					this.inputStream.skip(this.splitbyte);
				}

				this.parms = new HashMap<>();
				if (null == this.headers) {
					this.headers = new HashMap<>();
				} else {
					this.headers.clear();
				}

				// Create a BufferedReader for parsing the header.
				BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf, 0, this.rlen)));

				// Decode the header into parms and header java properties
				Map<String, String> pre = new HashMap<>();
				decodeHeader(hin, pre, this.parms, this.headers);

				this.method = pre.get("method");

				this.uri = pre.get("uri");

				String connection = this.headers.get("connection");
				boolean keepAlive = "HTTP/1.1".equals(protocolVersion) && (connection == null || !connection.matches("(?i).*close.*"));

				// Ok, now do the serve()

				// TODO: long body_size = getBodySize();
				// TODO: long pos_before_serve = this.inputStream.totalRead()
				// (requires implementation for totalRead())
				r = serve(this);
				// TODO: this.inputStream.skip(body_size -
				// (this.inputStream.totalRead() - pos_before_serve))

				if (r == null) {
					throw new ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
				} else {
					r.setRequestMethod(this.method);
					r.setKeepAlive(keepAlive);
					r.send(this.outputStream);
				}
				if (!keepAlive || r.isCloseConnection()) {
					throw new SocketException("NanoHttpd Shutdown");
				}
			} catch (SocketException e) {
				// throw it out to close socket object (finalAccept)
				throw e;
			} catch (SocketTimeoutException ste) {
				// treat socket timeouts the same way we treat socket exceptions
				// i.e. close the stream & finalAccept object by throwing the
				// exception up the call stack.
				throw ste;
			} catch (IOException ioe) {
				Response resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
				resp.send(this.outputStream);
				safeClose(this.outputStream);
			} catch (ResponseException re) {
				Response resp = newFixedLengthResponse(re.getStatus(), NanoHTTPD.MIME_PLAINTEXT, re.getMessage());
				resp.send(this.outputStream);
				safeClose(this.outputStream);
			} finally {
				safeClose(r);
			}
		}

		/**
		 * Find byte index separating header from body. It must be the last byte
		 * of the first two sequential new lines.
		 */
		private int findHeaderEnd(final byte[] buf, int rlen) {
			int splitbyte = 0;
			while (splitbyte + 1 < rlen) {

				// RFC2616
				if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && splitbyte + 3 < rlen && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
					return splitbyte + 4;
				}

				// tolerance
				if (buf[splitbyte] == '\n' && buf[splitbyte + 1] == '\n') {
					return splitbyte + 2;
				}
				splitbyte++;
			}
			return 0;
		}

		@Override
		public final Map<String, String> getHeaders() {
			return this.headers;
		}

		@Override
		public final InputStream getInputStream() {
			return this.inputStream;
		}

		@Override
		public final String getMethod() {
			return this.method;
		}

		@Override
		public final Map<String, List<String>> getParameters() {
			return this.parms;
		}

		@Override
		public String getQueryParameterString() {
			return this.queryParameterString;
		}

		@Override
		public final String getUri() {
			return this.uri;
		}

		/**
		 * Deduce body length in bytes. Either from "content-length" header or
		 * read bytes.
		 */
		public long getBodySize() {
			if (this.headers.containsKey("content-length")) {
				return Long.parseLong(this.headers.get("content-length"));
			} else if (this.splitbyte < this.rlen) {
				return this.rlen - this.splitbyte;
			}
			return 0;
		}

		@Override
		public String getRemoteIpAddress() {
			return this.remoteIp;
		}

		@Override
		public String getRemoteHostName() {
			return this.remoteHostname;
		}
	}

	/**
	 * Handles one session, i.e. parses the HTTP request and returns the
	 * response.
	 */
	public interface IHTTPSession {

		void execute() throws IOException;

		Map<String, String> getHeaders();

		InputStream getInputStream();

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

	/**
	 * HTTP response. Return one of these from serve().
	 */
	public static class Response implements Closeable {

		public interface IStatus {

			String getDescription();

			int getRequestStatus();
		}

		/**
		 * Some HTTP response status codes
		 */
		public enum Status implements IStatus {
			SWITCH_PROTOCOL(101, "Switching Protocols"),

			OK(200, "OK"),
			CREATED(201, "Created"),
			ACCEPTED(202, "Accepted"),
			NO_CONTENT(204, "No Content"),
			PARTIAL_CONTENT(206, "Partial Content"),
			MULTI_STATUS(207, "Multi-Status"),

			REDIRECT(301, "Moved Permanently"),
			/**
			 * Many user agents mishandle 302 in ways that violate the RFC1945
			 * spec (i.e., redirect a POST to a GET). 303 and 307 were added in
			 * RFC2616 to address this. You should prefer 303 and 307 unless the
			 * calling user agent does not support 303 and 307 functionality
			 */
			@Deprecated
			FOUND(302, "Found"),
			REDIRECT_SEE_OTHER(303, "See Other"),
			NOT_MODIFIED(304, "Not Modified"),
			TEMPORARY_REDIRECT(307, "Temporary Redirect"),

			BAD_REQUEST(400, "Bad Request"),
			UNAUTHORIZED(401, "Unauthorized"),
			FORBIDDEN(403, "Forbidden"),
			NOT_FOUND(404, "Not Found"),
			METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
			NOT_ACCEPTABLE(406, "Not Acceptable"),
			REQUEST_TIMEOUT(408, "Request Timeout"),
			CONFLICT(409, "Conflict"),
			GONE(410, "Gone"),
			LENGTH_REQUIRED(411, "Length Required"),
			PRECONDITION_FAILED(412, "Precondition Failed"),
			PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
			UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
			RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
			EXPECTATION_FAILED(417, "Expectation Failed"),
			TOO_MANY_REQUESTS(429, "Too Many Requests"),

			INTERNAL_ERROR(500, "Internal Server Error"),
			NOT_IMPLEMENTED(501, "Not Implemented"),
			SERVICE_UNAVAILABLE(503, "Service Unavailable"),
			UNSUPPORTED_HTTP_VERSION(505, "HTTP Version Not Supported");

			private final int requestStatus;

			private final String description;

			Status(int requestStatus, String description) {
				this.requestStatus = requestStatus;
				this.description = description;
			}

			public static Status lookup(int requestStatus) {
				for (Status status : Status.values()) {
					if (status.getRequestStatus() == requestStatus) {
						return status;
					}
				}
				return null;
			}

			@Override
			public String getDescription() {
				return "" + this.requestStatus + " " + this.description;
			}

			@Override
			public int getRequestStatus() {
				return this.requestStatus;
			}

		}

		/**
		 * Output stream that will automatically send every write to the wrapped
		 * OutputStream according to chunked transfer:
		 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1
		 */
		private static class ChunkedOutputStream extends FilterOutputStream {

			public ChunkedOutputStream(OutputStream out) {
				super(out);
			}

			@Override
			public void write(int b) throws IOException {
				byte[] data = {
						(byte) b
				};
				write(data, 0, 1);
			}

			@Override
			public void write(byte[] b) throws IOException {
				write(b, 0, b.length);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				if (len == 0)
					return;
				out.write(String.format("%x\r\n", len).getBytes());
				out.write(b, off, len);
				out.write("\r\n".getBytes());
			}

			public void finish() throws IOException {
				out.write("0\r\n\r\n".getBytes());
			}

		}

		/**
		 * HTTP status code after processing, e.g. "200 OK", Status.OK
		 */
		private IStatus status;

		/**
		 * MIME type of content, e.g. "text/html"
		 */
		private String mimeType;

		/**
		 * Data of the response, may be null.
		 */
		private InputStream data;

		private long contentLength;

		/**
		 * Headers for the HTTP response. Use addHeader() to add lines. the
		 * lowercase map is automatically kept up to date.
		 */
		private final Map<String, String> header = new HashMap<String, String>() {

			@Override
			public String put(String key, String value) {
				lowerCaseHeader.put(key == null ? key : key.toLowerCase(), value);
				return super.put(key, value);
			};
		};

		/**
		 * copy of the header map with all the keys lowercase for faster
		 * searching.
		 */
		private final Map<String, String> lowerCaseHeader = new HashMap<>();

		/**
		 * The request method that spawned this response.
		 */
		private String requestMethod;

		/**
		 * Use chunkedTransfer
		 */
		private boolean chunkedTransfer;

		private boolean keepAlive;

		/**
		 * Creates a fixed length response if totalBytes>=0, otherwise chunked.
		 */
		protected Response(IStatus status, String mimeType, InputStream data, long totalBytes) {
			this.status = status;
			this.mimeType = mimeType;
			if (data == null) {
				this.data = new ByteArrayInputStream(new byte[0]);
				this.contentLength = 0L;
			} else {
				this.data = data;
				this.contentLength = totalBytes;
			}
			this.chunkedTransfer = this.contentLength < 0;
			keepAlive = true;
		}

		@Override
		public void close() throws IOException {
			if (this.data != null) {
				this.data.close();
			}
		}

		/**
		 * Adds given line to the header.
		 */
		public void addHeader(String name, String value) {
			this.header.put(name, value);
		}

		/**
		 * Indicate to close the connection after the Response has been sent.
		 *
		 * @param close
		 *              {@code true} to hint connection closing, {@code false} to
		 *              let connection be closed by client.
		 */
		public void closeConnection(boolean close) {
			if (close)
				this.header.put("connection", "close");
			else
				this.header.remove("connection");
		}

		/**
		 * @return {@code true} if connection is to be closed after this
		 *         Response has been sent.
		 */
		public boolean isCloseConnection() {
			return "close".equals(getHeader("connection"));
		}

		public InputStream getData() {
			return this.data;
		}

		public String getHeader(String name) {
			return this.lowerCaseHeader.get(name.toLowerCase());
		}

		public String getMimeType() {
			return this.mimeType;
		}

		public String getRequestMethod() {
			return this.requestMethod;
		}

		public IStatus getStatus() {
			return this.status;
		}

		public void setKeepAlive(boolean useKeepAlive) {
			this.keepAlive = useKeepAlive;
		}

		/**
		 * Sends given response to the socket.
		 */
		protected void send(OutputStream outputStream) {
			SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
			gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));

			try {
				if (this.status == null) {
					throw new Error("sendResponse(): Status can't be null.");
				}
				PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, new ContentType(this.mimeType).getEncoding())), false);
				pw.append("HTTP/1.1 ").append(this.status.getDescription()).append(" \r\n");
				if (this.mimeType != null) {
					printHeader(pw, "Content-Type", this.mimeType);
				}
				if (getHeader("date") == null) {
					printHeader(pw, "Date", gmtFrmt.format(new Date()));
				}
				for (Entry<String, String> entry : this.header.entrySet()) {
					printHeader(pw, entry.getKey(), entry.getValue());
				}
				if (getHeader("connection") == null) {
					printHeader(pw, "Connection", (this.keepAlive ? "keep-alive" : "close"));
				}
				long pending = this.data != null ? this.contentLength : 0;
				if (!"HEAD".equals(this.requestMethod) && this.chunkedTransfer) {
					printHeader(pw, "Transfer-Encoding", "chunked");
				} else {
					pending = sendContentLengthHeaderIfNotAlreadyPresent(pw, pending);
				}
				pw.append("\r\n");
				pw.flush();
				sendBodyWithCorrectTransferAndEncoding(outputStream, pending);
				outputStream.flush();
				safeClose(this.data);
			} catch (IOException ioe) {
				NanoHTTPD.LOG.log(Level.SEVERE, "Could not send response to the client", ioe);
			}
		}

		protected void printHeader(PrintWriter pw, String key, String value) {
			pw.append(key).append(": ").append(value).append("\r\n");
		}

		protected long sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, long defaultSize) {
			String contentLengthString = getHeader("content-length");
			long size = defaultSize;
			if (contentLengthString != null) {
				try {
					size = Long.parseLong(contentLengthString);
				} catch (NumberFormatException ex) {
					LOG.severe("content-length was no number " + contentLengthString);
				}
			}
			pw.print("Content-Length: " + size + "\r\n");
			return size;
		}

		private void sendBodyWithCorrectTransferAndEncoding(OutputStream outputStream, long pending) throws IOException {
			if (!"HEAD".equals(this.requestMethod) && this.chunkedTransfer) {
				@SuppressWarnings("resource")
				ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
				sendBody(chunkedOutputStream, -1);
				chunkedOutputStream.finish();
			} else {
				sendBody(outputStream, pending);
			}
		}

		/**
		 * Sends the body to the specified OutputStream. The pending parameter
		 * limits the maximum amounts of bytes sent unless it is -1, in which
		 * case everything is sent.
		 *
		 * @param outputStream
		 *                     the OutputStream to send data to
		 * @param pending
		 *                     -1 to send everything, otherwise sets a max limit to the
		 *                     number of bytes sent
		 * @throws IOException
		 *                     if something goes wrong while sending the data.
		 */
		private void sendBody(OutputStream outputStream, long pending) throws IOException {
			long BUFFER_SIZE = 16 * 1024;
			byte[] buff = new byte[(int) BUFFER_SIZE];
			boolean sendEverything = pending == -1;
			while (pending > 0 || sendEverything) {
				long bytesToRead = sendEverything ? BUFFER_SIZE : Math.min(pending, BUFFER_SIZE);
				int read = this.data.read(buff, 0, (int) bytesToRead);
				if (read <= 0) {
					break;
				}
				outputStream.write(buff, 0, read);
				if (!sendEverything) {
					pending -= read;
				}
			}
		}

		public void setChunkedTransfer(boolean chunkedTransfer) {
			this.chunkedTransfer = chunkedTransfer;
		}

		public void setData(InputStream data) {
			this.data = data;
		}

		public void setMimeType(String mimeType) {
			this.mimeType = mimeType;
		}

		public void setRequestMethod(String requestMethod) {
			this.requestMethod = requestMethod;
		}

		public void setStatus(IStatus status) {
			this.status = status;
		}
	}

	public static final class ResponseException extends Exception {

		private static final long serialVersionUID = 6569838532917408380L;

		private final Response.Status status;

		public ResponseException(Response.Status status, String message) {
			super(message);
			this.status = status;
		}

		public ResponseException(Response.Status status, String message, Exception e) {
			super(message, e);
			this.status = status;
		}

		public Response.Status getStatus() {
			return this.status;
		}
	}

	/**
	 * The runnable that will be used for the main listening thread.
	 */
	public class ServerRunnable implements Runnable {

		private final int timeout;

		private IOException bindException;

		private boolean hasBinded = false;

		public ServerRunnable(int timeout) {
			this.timeout = timeout;
		}

		@Override
		public void run() {
			try {
				myServerSocket.bind(hostname != null ? new InetSocketAddress(hostname, myPort) : new InetSocketAddress(myPort));
				hasBinded = true;
			} catch (IOException e) {
				this.bindException = e;
				return;
			}
			do {
				try {
					@SuppressWarnings("resource")
					final Socket finalAccept = NanoHTTPD.this.myServerSocket.accept();
					if (this.timeout > 0) {
						finalAccept.setSoTimeout(this.timeout);
					}
					@SuppressWarnings("resource")
					final InputStream inputStream = finalAccept.getInputStream();
					NanoHTTPD.this.asyncRunner.exec(createClientHandler(finalAccept, inputStream));
				} catch (IOException e) {
					NanoHTTPD.LOG.log(Level.FINE, "Communication with the client broken", e);
				}
			} while (!NanoHTTPD.this.myServerSocket.isClosed());
		}
	}

	/**
	 * Factory to create ServerSocketFactories.
	 */
	public interface ServerSocketFactory {

		public ServerSocket create() throws IOException;

	}

	/**
	 * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
	 * This is required as the Keep-Alive HTTP connections would otherwise block
	 * the socket reading thread forever (or as long the browser is open).
	 */
	public static final int SOCKET_READ_TIMEOUT = 5000;

	/**
	 * Common MIME type for dynamic content: plain text
	 */
	public static final String MIME_PLAINTEXT = "text/plain";

	/**
	 * Common MIME type for dynamic content: html
	 */
	public static final String MIME_HTML = "text/html";

	/**
	 * logger to log to.
	 */
	private static final Logger LOG = Logger.getLogger(NanoHTTPD.class.getName());

	private static final void safeClose(Object closeable) {
		try {
			if (closeable != null) {
				if (closeable instanceof Closeable) {
					((Closeable) closeable).close();
				} else if (closeable instanceof Socket) {
					((Socket) closeable).close();
				} else if (closeable instanceof ServerSocket) {
					((ServerSocket) closeable).close();
				} else {
					throw new IllegalArgumentException("Unknown object to close");
				}
			}
		} catch (IOException e) {
			NanoHTTPD.LOG.log(Level.SEVERE, "Could not close", e);
		}
	}

	private final String hostname;

	private final int myPort;

	private volatile ServerSocket myServerSocket;

	private ServerSocketFactory serverSocketFactory = new DefaultServerSocketFactory();

	private Thread myThread;

	/**
	 * Pluggable strategy for asynchronously executing requests.
	 */
	protected AsyncRunner asyncRunner;

	/**
	 * Constructs an HTTP server on given port.
	 */
	public NanoHTTPD(int port) {
		this(null, port);
	}

	// -------------------------------------------------------------------------------
	// //
	//
	// Threading Strategy.
	//
	// -------------------------------------------------------------------------------
	// //

	/**
	 * Constructs an HTTP server on given hostname and port.
	 */
	public NanoHTTPD(String hostname, int port) {
		this.hostname = hostname;
		this.myPort = port;
		setAsyncRunner(new DefaultAsyncRunner());
	}

	/**
	 * Forcibly closes all connections that are open.
	 */
	public synchronized void closeAllConnections() {
		stop();
	}

	/**
	 * create a instance of the client handler, subclasses can return a subclass
	 * of the ClientHandler.
	 *
	 * @param finalAccept
	 *                    the socket the cleint is connected to
	 * @param inputStream
	 *                    the input stream
	 * @return the client handler
	 */
	protected ClientHandler createClientHandler(final Socket finalAccept, final InputStream inputStream) {
		return new ClientHandler(inputStream, finalAccept);
	}

	/**
	 * Instantiate the server runnable, can be overwritten by subclasses to
	 * provide a subclass of the ServerRunnable.
	 *
	 * @param timeout
	 *                the socet timeout to use.
	 * @return the server runnable.
	 */
	protected ServerRunnable createServerRunnable(final int timeout) {
		return new ServerRunnable(timeout);
	}

	/**
	 * Decode percent encoded <code>String</code> values.
	 *
	 * @param str
	 *            the percent encoded <code>String</code>
	 * @return expanded form of the input, for example "foo%20bar" becomes
	 *         "foo bar"
	 */
	protected static String decodePercent(String str) {
		String decoded = null;
		try {
			decoded = URLDecoder.decode(str, "UTF8");
		} catch (UnsupportedEncodingException ignored) {
			NanoHTTPD.LOG.log(Level.WARNING, "Encoding not supported, ignored", ignored);
		}
		return decoded;
	}

	public final int getListeningPort() {
		return this.myServerSocket == null ? -1 : this.myServerSocket.getLocalPort();
	}

	public final boolean isAlive() {
		return wasStarted() && !this.myServerSocket.isClosed() && this.myThread.isAlive();
	}

	public ServerSocketFactory getServerSocketFactory() {
		return serverSocketFactory;
	}

	public void setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
		this.serverSocketFactory = serverSocketFactory;
	}

	public String getHostname() {
		return hostname;
	}

	/**
	 * Create a response with unknown length (using HTTP 1.1 chunking).
	 */
	public static Response newChunkedResponse(IStatus status, String mimeType, InputStream data) {
		return new Response(status, mimeType, data, -1);
	}

	/**
	 * Create a response with known length.
	 */
	public static Response newFixedLengthResponse(IStatus status, String mimeType, InputStream data, long totalBytes) {
		return new Response(status, mimeType, data, totalBytes);
	}

	/**
	 * Create a text response with known length.
	 */
	public static Response newFixedLengthResponse(IStatus status, String mimeType, String txt) {
		ContentType contentType = new ContentType(mimeType);
		if (txt == null) {
			return newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(new byte[0]), 0);
		} else {
			byte[] bytes;
			try {
				CharsetEncoder newEncoder = Charset.forName(contentType.getEncoding()).newEncoder();
				if (!newEncoder.canEncode(txt)) {
					contentType = contentType.tryUTF8();
				}
				bytes = txt.getBytes(contentType.getEncoding());
			} catch (UnsupportedEncodingException e) {
				NanoHTTPD.LOG.log(Level.SEVERE, "encoding problem, responding nothing", e);
				bytes = new byte[0];
			}
			return newFixedLengthResponse(status, contentType.getContentTypeHeader(), new ByteArrayInputStream(bytes), bytes.length);
		}
	}

	/**
	 * Create a text response with known length.
	 */
	public static Response newFixedLengthResponse(String msg) {
		return newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, msg);
	}

	/**
	 * Override this to customize the server.
	 * <p/>
	 * <p/>
	 * (By default, this returns a 404 "Not Found" plain text error response.)
	 *
	 * @param session
	 *                The HTTP session
	 * @return HTTP response, see class Response for details
	 */
	public Response serve(IHTTPSession session) {
		return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
	}

	/**
	 * Pluggable strategy for asynchronously executing requests.
	 *
	 * @param asyncRunner
	 *                    new strategy for handling threads.
	 */
	public void setAsyncRunner(AsyncRunner asyncRunner) {
		this.asyncRunner = asyncRunner;
	}

	/**
	 * Start the server.
	 *
	 * @throws IOException
	 *                     if the socket is in use.
	 */
	public void start() throws IOException {
		start(NanoHTTPD.SOCKET_READ_TIMEOUT);
	}

	/**
	 * Starts the server (in setDaemon(true) mode).
	 */
	public void start(final int timeout) throws IOException {
		start(timeout, true);
	}

	/**
	 * Start the server.
	 *
	 * @param timeout
	 *                timeout to use for socket connections.
	 * @param daemon
	 *                start the thread daemon or not.
	 * @throws IOException
	 *                     if the socket is in use.
	 */
	public void start(final int timeout, boolean daemon) throws IOException {
		this.myServerSocket = this.getServerSocketFactory().create();
		this.myServerSocket.setReuseAddress(true);

		ServerRunnable serverRunnable = createServerRunnable(timeout);
		this.myThread = new Thread(serverRunnable);
		this.myThread.setDaemon(daemon);
		this.myThread.setName("NanoHttpd Main Listener");
		this.myThread.start();
		while (!serverRunnable.hasBinded && serverRunnable.bindException == null) {
			try {
				Thread.sleep(10L);
			} catch (Throwable e) {
				// on android this may not be allowed, that's why we
				// catch throwable the wait should be very short because we are
				// just waiting for the bind of the socket
			}
		}
		if (serverRunnable.bindException != null) {
			throw serverRunnable.bindException;
		}
	}

	/**
	 * Stop the server.
	 */
	public void stop() {
		try {
			safeClose(this.myServerSocket);
			this.asyncRunner.closeAll();
			if (this.myThread != null) {
				this.myThread.join();
			}
		} catch (Exception e) {
			NanoHTTPD.LOG.log(Level.SEVERE, "Could not stop all connections", e);
		}
	}

	public final boolean wasStarted() {
		return this.myServerSocket != null && this.myThread != null;
	}
}
