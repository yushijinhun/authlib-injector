/*
 * Copyright (C) 2021  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import static java.util.Objects.requireNonNull;
import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.ERROR;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * HTTP response. Return one of these from serve().
 */
public class Response implements Closeable {

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
	 * Headers for the HTTP response. Use addHeader() to add lines.
	 */
	private final Map<String, String> headers = new LinkedHashMap<>();

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
		this.headers.put(name.toLowerCase(Locale.ROOT), requireNonNull(value));
	}

	public String getHeader(String name) {
		return this.headers.get(name.toLowerCase(Locale.ROOT));
	}

	public InputStream getData() {
		return this.data;
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
	protected void send(OutputStream outputStream) throws IOException {
		if (this.status == null) {
			throw new IllegalStateException("sendResponse(): Status can't be null.");
		}

		SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
		gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));

		PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, new ContentType(this.mimeType).getEncoding())), false);
		pw.append("HTTP/1.1 ").append(this.status.getDescription()).append(" \r\n");
		if (this.mimeType != null) {
			printHeader(pw, "Content-Type", this.mimeType);
		}
		if (getHeader("date") == null) {
			printHeader(pw, "Date", gmtFrmt.format(new Date()));
		}
		this.headers.forEach((name, value) -> printHeader(pw, name, value));
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
		NanoHTTPD.safeClose(this.data);
	}

	protected void printHeader(PrintWriter pw, String key, String value) {
		pw.append(key).append(": ").append(value).append("\r\n");
	}

	protected long sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, long defaultSize) {
		String contentLengthString = getHeader("content-length");
		if (contentLengthString == null) {
			pw.print("Content-Length: " + defaultSize + "\r\n");
			return defaultSize;
		} else {
			long size = defaultSize;
			try {
				size = Long.parseLong(contentLengthString);
			} catch (NumberFormatException ex) {
				log(ERROR, "content-length was not number " + contentLengthString);
			}
			return size;
		}
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

	/**
	 * Create a text response with known length.
	 */
	public static Response newFixedLength(IStatus status, String mimeType, String txt) {
		ContentType contentType = new ContentType(mimeType);
		if (txt == null) {
			return newFixedLength(status, mimeType, new ByteArrayInputStream(new byte[0]), 0);
		} else {
			byte[] bytes;
			try {
				CharsetEncoder newEncoder = Charset.forName(contentType.getEncoding()).newEncoder();
				if (!newEncoder.canEncode(txt)) {
					contentType = contentType.tryUTF8();
				}
				bytes = txt.getBytes(contentType.getEncoding());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e); // never happens, utf-8 is always available
			}
			return newFixedLength(status, contentType.getContentTypeHeader(), new ByteArrayInputStream(bytes), bytes.length);
		}
	}

	/**
	 * Create a response with known length.
	 */
	public static Response newFixedLength(IStatus status, String mimeType, InputStream data, long totalBytes) {
		return new Response(status, mimeType, data, totalBytes);
	}

	/**
	 * Create a response with unknown length (using HTTP 1.1 chunking).
	 */
	public static Response newChunked(IStatus status, String mimeType, InputStream data) {
		return new Response(status, mimeType, data, -1);
	}
}
