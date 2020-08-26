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
/*
 * NanoHttpd-Core
 *
 * Copyright (C) 2012 - 2015 nanohttpd
 *
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
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.logging.Level;

class HTTPSession implements IHTTPSession {

	public static class ConnectionCloseException extends SocketException {}

	public static final int BUFSIZE = 8192;

	private final OutputStream outputStream;
	private final BufferedInputStream inputStream;
	private final InetSocketAddress remoteAddr;

	private String uri;
	private String method;
	private String queryParameterString;
	private Map<String, List<String>> parms;
	private Map<String, String> headers;
	private String protocolVersion;

	private InputStream parsedInputStream;

	private boolean expect100Continue;
	private boolean continueSent;
	private boolean isServing;
	private final Object servingLock = new Object();

	public HTTPSession(InputStream inputStream, OutputStream outputStream, InetSocketAddress remoteAddr) {
		this.inputStream = new BufferedInputStream(inputStream, BUFSIZE);
		this.outputStream = outputStream;
		this.remoteAddr = remoteAddr;
	}

	private ByteArrayInputStream readHeader() throws IOException {
		// Read the first 8192 bytes.
		// The full header should fit in here.
		// Apache's default header limit is 8KB.
		// Do NOT assume that a single read will get the entire header
		// at once!
		byte[] buf = new byte[BUFSIZE];
		int splitbyte = 0;
		int rlen = 0;

		int read = -1;
		this.inputStream.mark(BUFSIZE);
		try {
			read = this.inputStream.read(buf, 0, BUFSIZE);
		} catch (IOException e) {
			NanoHTTPD.safeClose(this.inputStream);
			NanoHTTPD.safeClose(this.outputStream);
			throw new ConnectionCloseException();
		}
		if (read == -1) {
			// socket was been closed
			NanoHTTPD.safeClose(this.inputStream);
			NanoHTTPD.safeClose(this.outputStream);
			throw new ConnectionCloseException();
		}
		while (read > 0) {
			rlen += read;
			splitbyte = findHeaderEnd(buf, rlen);
			if (splitbyte > 0) {
				break;
			}
			read = this.inputStream.read(buf, rlen, BUFSIZE - rlen);
		}

		if (splitbyte < rlen) {
			this.inputStream.reset();
			this.inputStream.skip(splitbyte);
		}

		return new ByteArrayInputStream(buf, 0, rlen);
	}

	private void parseHeader(BufferedReader in) throws ResponseException {
		try {
			String requestLine = in.readLine();
			if (requestLine == null) {
				throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
			}

			StringTokenizer st = new StringTokenizer(requestLine);
			if (!st.hasMoreTokens()) {
				throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
			}

			this.method = st.nextToken();

			if (!st.hasMoreTokens()) {
				throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Missing URI.");
			}

			String rawUri = st.nextToken();

			// Decode parameters from the URI
			int qmi = rawUri.indexOf('?');
			if (qmi >= 0) {
				this.queryParameterString = rawUri.substring(qmi + 1);
				this.parms = Collections.unmodifiableMap(decodeParms(this.queryParameterString));
				this.uri = decodePercent(rawUri.substring(0, qmi));
			} else {
				this.queryParameterString = null;
				this.parms = Collections.emptyMap();
				this.uri = decodePercent(rawUri);
			}

			// If there's another token, its protocol version,
			// followed by HTTP headers.
			// NOTE: this now forces header names lower case since they are
			// case insensitive and vary by client.
			if (st.hasMoreTokens()) {
				this.protocolVersion = st.nextToken();
			} else {
				this.protocolVersion = "HTTP/1.1";
				NanoHTTPD.LOG.log(Level.FINE, "no protocol version specified, strange. Assuming HTTP/1.1.");
			}

			Map<String, String> headers = new LinkedHashMap<>();
			String line = in.readLine();
			while (line != null && !line.trim().isEmpty()) {
				int p = line.indexOf(':');
				if (p >= 0) {
					headers.put(line.substring(0, p).trim().toLowerCase(Locale.ROOT), line.substring(p + 1).trim());
				}
				line = in.readLine();
			}
			this.headers = Collections.unmodifiableMap(headers);

		} catch (IOException ioe) {
			throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
		}
	}

	public void execute(Function<IHTTPSession, Response> handler) throws IOException {
		Response r = null;
		try {
			parseHeader(new BufferedReader(new InputStreamReader(readHeader(), ISO_8859_1)));

			String transferEncoding = this.headers.get("transfer-encoding");
			String contentLengthStr = this.headers.get("content-length");
			if (transferEncoding != null && contentLengthStr == null) {
				if ("chunked".equals(transferEncoding)) {
					this.parsedInputStream = new ChunkedInputStream(this.inputStream);
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
				this.parsedInputStream = new FixedLengthInputStream(this.inputStream, contentLength);

			} else if (transferEncoding != null && contentLengthStr != null) {
				throw new ResponseException(Status.BAD_REQUEST, "Content-Length and Transfer-Encoding cannot exist at the same time.");

			} else /* if both are null */ {
				// no request payload
				this.parsedInputStream = null;
			}

			this.expect100Continue = "HTTP/1.1".equals(this.protocolVersion)
					&& "100-continue".equals(this.headers.get("expect"))
					&& this.parsedInputStream != null;
			this.continueSent = false;

			// Ok, now do the serve()
			this.isServing = true;
			try {
				r = handler.apply(this);
			} finally {
				synchronized (this.servingLock) {
					this.isServing = false;
				}
			}

			if (!(this.parsedInputStream == null || (this.expect100Continue && !this.continueSent))) {
				// consume the input
				while (this.parsedInputStream.read() != -1)
					;
			}

			boolean keepAlive = "HTTP/1.1".equals(this.protocolVersion) && !"close".equals(this.headers.get("connection"));

			if (r == null) {
				throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
			} else {
				r.setRequestMethod(this.method);
				r.setKeepAlive(keepAlive);
				r.send(this.outputStream);
			}
			if (!keepAlive || "close".equals(r.getHeader("connection"))) {
				throw new ConnectionCloseException();
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
			NanoHTTPD.safeClose(this.outputStream);
		} catch (ResponseException re) {
			Response resp = Response.newFixedLength(re.getStatus(), NanoHTTPD.MIME_PLAINTEXT, re.getMessage());
			resp.send(this.outputStream);
			NanoHTTPD.safeClose(this.outputStream);
		} finally {
			NanoHTTPD.safeClose(r);
		}
	}

	@Override
	public final Map<String, String> getHeaders() {
		return this.headers;
	}

	@Override
	public final InputStream getInputStream() throws IOException {
		synchronized (this.servingLock) {
			if (!this.isServing) {
				throw new IllegalStateException();
			}
			if (this.expect100Continue && !this.continueSent) {
				this.continueSent = true;
				this.outputStream.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(ISO_8859_1));
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

	@Override
	public InetSocketAddress getRemoteAddress() {
		return this.remoteAddr;
	}

	/**
	 * Find byte index separating header from body. It must be the last byte
	 * of the first two sequential new lines.
	 */
	private static int findHeaderEnd(final byte[] buf, int rlen) {
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

	/**
	 * Decodes parameters in percent-encoded URI-format ( e.g.
	 * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
	 * Map.
	 */
	private static Map<String, List<String>> decodeParms(String parms) {
		Map<String, List<String>> result = new LinkedHashMap<>();
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

			List<String> values = result.get(key);
			if (values == null) {
				values = new ArrayList<>();
				result.put(key, values);
			}

			values.add(value);
		}
		return result;
	}
}
