package org.to2mbn.authlibinjector.test;

import static org.junit.Assert.assertArrayEquals;
import static org.to2mbn.authlibinjector.util.KeyUtils.decodePublicKey;
import org.junit.Test;

public class KeyUtilsTest {

	@Test
	public void testDecodePublicKey1() {
		assertArrayEquals(new byte[] { 127, 127, 127, 127 },
				decodePublicKey("-----BEGIN PUBLIC KEY-----f39/fw==-----END PUBLIC KEY-----"));
	}

	@Test
	public void testDecodePublicKey2() {
		assertArrayEquals(new byte[] { 127, 127, 127, 127 },
				decodePublicKey("-----BEGIN PUBLIC KEY-----\nf\n39/fw==\n-----END PUBLIC KEY-----\n"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecodePublicKey3() {
		decodePublicKey("-----BEGIN PUBLIC KEY----- f39/fw== -----END PUBLIC KEY-----");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecodePublicKey4() {
		decodePublicKey("-----BEGIN PUBLIC KEY-----f39/fw==-----END NOT A PUBLIC KEY-----");
	}

}
