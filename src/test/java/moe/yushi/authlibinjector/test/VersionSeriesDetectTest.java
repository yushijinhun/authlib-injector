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
package moe.yushi.authlibinjector.test;

import static moe.yushi.authlibinjector.transform.MainArgumentsTransformer.inferVersionSeries;
import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.junit.Test;

public class VersionSeriesDetectTest {

	@Test
	public void testNone() {
		assertEquals(inferVersionSeries(new String[] {
				""
		}), Optional.empty());
	}

	@Test
	public void testHalf1() {
		assertEquals(inferVersionSeries(new String[] {
				"--assetIndex"
		}), Optional.empty());
	}

	@Test
	public void testHalf2() {
		assertEquals(inferVersionSeries(new String[] {
				"--width",
				"854",
				"--height",
				"480",
				"--username",
				"character2",
				"--version",
				"HMCL 3.2.SNAPSHOT",
				"--gameDir",
				"/home/yushijinhun/.minecraft",
				"--assetsDir",
				"/home/yushijinhun/.minecraft/assets",
				"--assetIndex"
		}), Optional.empty());
	}

	@Test
	public void testAmbiguity1() {
		assertEquals(inferVersionSeries(new String[] {
				"--username",
				"--assetIndex"
		}), Optional.empty());
	}

	@Test
	public void testAmbiguity2() {
		assertEquals(inferVersionSeries(new String[] {
				"--demo",
				"--assetIndex",
				"1.12"
		}), Optional.of("1.12"));
	}

	@Test
	public void test1_7_10() {
		assertEquals(inferVersionSeries(new String[] {
				"--width",
				"854",
				"--height",
				"480",
				"--username",
				"character2",
				"--version",
				"HMCL 3.2.SNAPSHOT",
				"--gameDir",
				"/home/yushijinhun/.minecraft",
				"--assetsDir",
				"/home/yushijinhun/.minecraft/assets",
				"--assetIndex",
				"1.7.10",
				"--uuid",
				"e448fdeff6394b3fac7aecc291446329",
				"--accessToken",
				"65a7fe601b02439fa90bea52b7d46ab5",
				"--userProperties",
				"{}",
				"--userType",
				"mojang"
		}), Optional.of("1.7.10"));
	}
}
