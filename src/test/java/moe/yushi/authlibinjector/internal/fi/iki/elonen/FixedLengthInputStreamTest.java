/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

@SuppressWarnings("resource")
public class FixedLengthInputStreamTest {

	@Test
	public void testRead1() throws IOException {
		byte[] data = new byte[] { 0x11, 0x22, 0x33, 0x44, 0x55 };
		ByteArrayInputStream underlying = new ByteArrayInputStream(data);
		InputStream in = new FixedLengthInputStream(underlying, 5);
		assertArrayEquals(data, asBytes(in));
		assertEquals(underlying.read(), -1);
	}

	@Test
	public void testRead2() throws IOException {
		byte[] data = new byte[] { 0x11, 0x22, 0x33, 0x44, 0x55 };
		ByteArrayInputStream underlying = new ByteArrayInputStream(data);
		InputStream in = new FixedLengthInputStream(underlying, 4);
		assertArrayEquals(Arrays.copyOf(data, 4), asBytes(in));
		assertEquals(underlying.read(), 0x55);
	}

	@Test
	public void testRead3() throws IOException {
		byte[] data = new byte[] { 0x11 };
		ByteArrayInputStream underlying = new ByteArrayInputStream(data);
		InputStream in = new FixedLengthInputStream(underlying, 0);
		assertArrayEquals(new byte[0], asBytes(in));
		assertEquals(underlying.read(), 0x11);
	}

	@Test(expected = EOFException.class)
	public void testReadEOF() throws IOException {
		byte[] data = new byte[] { 0x11, 0x22, 0x33, 0x44, 0x55 };
		InputStream in = new FixedLengthInputStream(new ByteArrayInputStream(data), 6);
		asBytes(in);
	}
}
