package moe.yushi.authlibinjector.test;

import static moe.yushi.authlibinjector.util.KeyUtils.decodePEMPublicKey;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class KeyUtilsTest {

	@Test
	public void testDecodePublicKey1() {
		assertArrayEquals(new byte[] { 127, 127, 127, 127 },
				decodePEMPublicKey("-----BEGIN PUBLIC KEY-----f39/fw==-----END PUBLIC KEY-----"));
	}

	@Test
	public void testDecodePublicKey2() {
		assertArrayEquals(new byte[] { 127, 127, 127, 127 },
				decodePEMPublicKey("-----BEGIN PUBLIC KEY-----\nf\n39/fw==\n-----END PUBLIC KEY-----\n"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecodePublicKey3() {
		decodePEMPublicKey("-----BEGIN PUBLIC KEY----- f39/fw== -----END PUBLIC KEY-----");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDecodePublicKey4() {
		decodePEMPublicKey("-----BEGIN PUBLIC KEY-----f39/fw==-----END NOT A PUBLIC KEY-----");
	}

}
