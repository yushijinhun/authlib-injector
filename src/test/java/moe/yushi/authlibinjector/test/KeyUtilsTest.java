/*
 * Copyright (C) 2022  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector.test;

import static moe.yushi.authlibinjector.util.KeyUtils.decodePEMPublicKey;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

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

	@Test
	public void testDecodePublicKey3() {
		assertThrows(IllegalArgumentException.class,
				() -> decodePEMPublicKey("-----BEGIN PUBLIC KEY----- f39/fw== -----END PUBLIC KEY-----"));
	}

	@Test
	public void testDecodePublicKey4() {
		assertThrows(IllegalArgumentException.class,
				() -> decodePEMPublicKey("-----BEGIN PUBLIC KEY-----f39/fw==-----END NOT A PUBLIC KEY-----"));
	}

}
