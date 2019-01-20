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
