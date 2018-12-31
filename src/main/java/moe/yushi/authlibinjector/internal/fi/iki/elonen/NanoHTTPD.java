package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import static java.nio.charset.StandardCharsets.US_ASCII;

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
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple, tiny, nicely embeddable HTTP server in Java
 * <p/>
 * <p/>
 * NanoHTTPD
 * <p>
 * Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen,
 * 2010 by Konstantinos Togias
 * </p>
 * See the separate "META-INF/licenses/nanohttpd.txt" file for the distribution license (Modified BSD licence)
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

	protected class HTTPSession implements IHTTPSession {

		public static final int BUFSIZE = 8192;

		public static final int MAX_HEADER_SIZE = 1024;

		private final OutputStream outputStream;

		private final BufferedInputStream inputStream;

		private InputStream parsedInputStream;

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

		private boolean expect100Continue;
		private boolean continueSent;
		private boolean isServing;
		private final Object servingLock = new Object();

		public HTTPSession(InputStream inputStream, OutputStream outputStream) {
			this.inputStream = new BufferedInputStream(inputStream, HTTPSession.BUFSIZE);
			this.outputStream = outputStream;
		}

		public HTTPSession(InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
			this.inputStream = new BufferedInputStream(inputStream, HTTPSession.BUFSIZE);
			this.outputStream = outputStream;
			this.remoteIp = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "127.0.0.1" : inetAddress.getHostAddress();
			this.remoteHostname = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "localhost" : inetAddress.getHostName();
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
					throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
				}

				pre.put("method", st.nextToken());

				if (!st.hasMoreTokens()) {
					throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
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
				throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
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
				BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf, 0, this.rlen), US_ASCII));

				// Decode the header into parms and header java properties
				Map<String, String> pre = new HashMap<>();
				decodeHeader(hin, pre, this.parms, this.headers);

				this.method = pre.get("method");

				this.uri = pre.get("uri");

				String connection = this.headers.get("connection");
				boolean keepAlive = "HTTP/1.1".equals(protocolVersion) && (connection == null || !connection.matches("(?i).*close.*"));

				String transferEncoding = this.headers.get("transfer-encoding");
				String contentLengthStr = this.headers.get("content-length");
				if (transferEncoding != null && contentLengthStr == null) {
					if ("chunked".equals(transferEncoding)) {
						parsedInputStream = new ChunkedInputStream(inputStream);
					} else {
						throw new ResponseException(Status.NOT_IMPLEMENTED, "Unsupported Transfer-Encoding");
					}

				} else if (transferEncoding == null && contentLengthStr != null) {
					int contentLength = -1;
					try {
						contentLength = Integer.parseInt(contentLengthStr);
					} catch (NumberFormatException e) {
					}
					if (contentLength < 0) {
						throw new ResponseException(Status.BAD_REQUEST, "The request has an invalid Content-Length header.");
					}
					parsedInputStream = new FixedLengthInputStream(inputStream, contentLength);

				} else if (transferEncoding != null && contentLengthStr != null) {
					throw new ResponseException(Status.BAD_REQUEST, "Content-Length and Transfer-Encoding cannot exist at the same time.");

				} else /* if both are null */ {
					// no request payload
				}

				expect100Continue = "HTTP/1.1".equals(protocolVersion)
						&& "100-continue".equals(this.headers.get("expect"))
						&& parsedInputStream != null;

				// Ok, now do the serve()
				this.isServing = true;
				try {
					r = serve(this);
				} finally {
					synchronized (servingLock) {
						this.isServing = false;
					}
				}

				if (!(parsedInputStream == null || (expect100Continue && !continueSent))) {
					// consume the input
					while (parsedInputStream.read() != -1)
						;
				}

				if (r == null) {
					throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
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
				Response resp = Response.newFixedLength(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
				resp.send(this.outputStream);
				safeClose(this.outputStream);
			} catch (ResponseException re) {
				Response resp = Response.newFixedLength(re.getStatus(), NanoHTTPD.MIME_PLAINTEXT, re.getMessage());
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
		public final InputStream getInputStream() throws IOException {
			synchronized (servingLock) {
				if (!isServing) {
					throw new IllegalStateException();
				}
				if (expect100Continue && !continueSent) {
					continueSent = true;
					this.outputStream.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(US_ASCII));
				}
			}
			return this.parsedInputStream;
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
	static final Logger LOG = Logger.getLogger(NanoHTTPD.class.getName());

	static final void safeClose(Object closeable) {
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
	private static String decodePercent(String str) {
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
		return Response.newFixedLength(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
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
