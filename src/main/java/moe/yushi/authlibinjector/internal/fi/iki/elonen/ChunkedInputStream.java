package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yushijinhun
 */
class ChunkedInputStream extends InputStream {

	private final InputStream in;

	//            0 = end of chunk, \r\n hasn't been read
	//           -1 = begin of chunk
	//           -2 = closed
	// other values = bytes remaining in current chunk
	private int currentRemaining = -1;

	public ChunkedInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public synchronized int read() throws IOException {
		if (currentRemaining == -2) {
			return -1;
		}
		if (currentRemaining == 0) {
			readCRLF();
			currentRemaining = -1;
		}
		if (currentRemaining == -1) {
			currentRemaining = readChunkLength();
			if (currentRemaining == 0) {
				readCRLF();
				currentRemaining = -2;
				return -1;
			}
		}
		int result = in.read();
		currentRemaining--;
		if (result == -1) {
			throw new EOFException();
		}
		return result;
	}

	private int readChunkLength() throws IOException {
		int length = 0;
		int b;
		for (;;) {
			b = in.read();
			if (b == -1) {
				throw new EOFException();
			}
			if (b == '\r') {
				b = in.read();
				if (b == -1) {
					throw new EOFException();
				} else if (b == '\n') {
					return length;
				} else {
					throw new IOException("LF is expected, read: " + b);
				}
			}
			int digit = hexDigit(b);
			if (digit == -1) {
				throw new IOException("Hex digit is expected, read: " + b);
			}
			if ((length & 0xf8000000) != 0) { // highest 5 bits must be zero
				throw new IOException("Chunk is too long");
			}
			length <<= 4;
			length += digit;
		}
	}

	private void readCRLF() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if (b1 == '\r' && b2 == '\n') {
			return;
		}
		if (b1 == -1 || b2 == -1) {
			throw new EOFException();
		}
		throw new IOException("CRLF is expected, read: " + b1 + " " + b2);
	}

	private static int hexDigit(int ch) {
		if (ch >= '0' && ch <= '9') {
			return ch - '0';
		} else if (ch >= 'a' && ch <= 'f') {
			return ch - 'a' + 10;
		} else if (ch >= 'A' && ch <= 'F') {
			return ch - 'A' + 10;
		} else {
			return -1;
		}
	}

	@Override
	public synchronized int available() throws IOException {
		if (currentRemaining > 0) {
			return Math.min(currentRemaining, in.available());
		} else {
			return 0;
		}
	}
}
