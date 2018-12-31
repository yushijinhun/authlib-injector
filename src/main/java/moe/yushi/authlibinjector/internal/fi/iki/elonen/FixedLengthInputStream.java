package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author yushijinhun
 */
class FixedLengthInputStream extends InputStream {

	private final InputStream in;
	private long remaining = 0;

	public FixedLengthInputStream(InputStream in, long length) {
		this.remaining = length;
		this.in = in;
	}

	@Override
	public synchronized int read() throws IOException {
		if (remaining > 0) {
			int result = in.read();
			if (result == -1) {
				throw new EOFException();
			}
			remaining--;
			return result;
		} else {
			return -1;
		}
	}

	@Override
	public synchronized int available() throws IOException {
		return Math.min(in.available(), (int) remaining);
	}
}
