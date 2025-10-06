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

import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_TEXT;
import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static moe.yushi.authlibinjector.util.Logging.Level.ERROR;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.HTTPSession.ConnectionCloseException;

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
	 * The runnable that will be used for every new client connection.
	 */
	private class ClientHandler implements Runnable {

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
				HTTPSession session = new HTTPSession(this.inputStream, outputStream, (InetSocketAddress) this.acceptSocket.getRemoteSocketAddress());
				while (!this.acceptSocket.isClosed()) {
					session.execute(NanoHTTPD.this::serve);
				}
			} catch (ConnectionCloseException e) {
				// When the socket is closed by the client,
				// we throw our own ConnectionCloseException
				// to break the "keep alive" loop above. If
				// the exception was anything other
				// than the expected SocketException OR a
				// SocketTimeoutException, print the
				// stacktrace
			} catch (Exception e) {
				log(ERROR, "Communication with the client broken, or an bug in the handler code", e);
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
	private static class AsyncRunner {

		private final AtomicLong requestCount = new AtomicLong();
		private final List<ClientHandler> running = new CopyOnWriteArrayList<>();

		public void closeAll() {
			for (ClientHandler clientHandler : this.running) {
				clientHandler.close();
			}
		}

		public void closed(ClientHandler clientHandler) {
			this.running.remove(clientHandler);
		}

		public void exec(ClientHandler clientHandler) {
			Thread t = new Thread(clientHandler);
			t.setDaemon(true);
			t.setName("NanoHttpd Request Processor (#" + this.requestCount.incrementAndGet() + ")");
			this.running.add(clientHandler);
			t.start();
		}
	}

	/**
	 * The runnable that will be used for the main listening thread.
	 */
	private class ServerRunnable implements Runnable {

		/**
		 * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
		 * This is required as the Keep-Alive HTTP connections would otherwise block
		 * the socket reading thread forever (or as long the browser is open).
		 */
		private static final int SOCKET_READ_TIMEOUT = 5000;

		private IOException bindException;

		private boolean hasBinded = false;

		@Override
		public void run() {
			try {
				serverSocket.bind(hostname != null ? new InetSocketAddress(hostname, port) : new InetSocketAddress(port));
				hasBinded = true;
			} catch (IOException e) {
				this.bindException = e;
				return;
			}
			do {
				try {
					@SuppressWarnings("resource")
					final Socket finalAccept = NanoHTTPD.this.serverSocket.accept();
					finalAccept.setSoTimeout(SOCKET_READ_TIMEOUT);
					@SuppressWarnings("resource")
					final InputStream inputStream = finalAccept.getInputStream();
					NanoHTTPD.this.asyncRunner.exec(new ClientHandler(inputStream, finalAccept));
				} catch (IOException e) {
					log(DEBUG, "Communication with the client broken", e);
				}
			} while (!NanoHTTPD.this.serverSocket.isClosed());
		}
	}

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
			log(ERROR, "Could not close", e);
		}
	}

	private final String hostname;
	private final int port;

	private volatile ServerSocket serverSocket;
	private Thread listenerThread;

	private final AsyncRunner asyncRunner = new AsyncRunner();

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
		this.port = port;
	}

	public final int getListeningPort() {
		return this.serverSocket == null ? -1 : this.serverSocket.getLocalPort();
	}

	public final boolean isAlive() {
		return wasStarted() && !this.serverSocket.isClosed() && this.listenerThread.isAlive();
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
		return Response.newFixedLength(Status.NOT_FOUND, CONTENT_TYPE_TEXT, "Not Found");
	}

	/**
	 * Starts the server in daemon mode.
	 */
	public void start() throws IOException {
		start(true);
	}

	/**
	 * Start the server.
	 *
	 * @param daemon
	 *                start the thread daemon or not.
	 * @throws IOException
	 *                     if the socket is in use.
	 */
	public void start(boolean daemon) throws IOException {
		this.serverSocket = new ServerSocket();
		this.serverSocket.setReuseAddress(true);

		ServerRunnable serverRunnable = new ServerRunnable();
		this.listenerThread = new Thread(serverRunnable);
		this.listenerThread.setDaemon(daemon);
		this.listenerThread.setName("NanoHttpd Main Listener");
		this.listenerThread.start();
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
			safeClose(this.serverSocket);
			this.asyncRunner.closeAll();
			if (this.listenerThread != null) {
				this.listenerThread.join();
			}
		} catch (Exception e) {
			log(ERROR, "Could not stop all connections", e);
		}
	}

	public final boolean wasStarted() {
		return this.serverSocket != null && this.listenerThread != null;
	}
}
