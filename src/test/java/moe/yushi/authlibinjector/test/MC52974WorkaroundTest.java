package moe.yushi.authlibinjector.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import moe.yushi.authlibinjector.transform.support.MC52974Workaround;

public class MC52974WorkaroundTest {

	private boolean checkEnabled(String args) {
		MC52974Workaround workaround = new MC52974Workaround();
		workaround.acceptMainArguments(args.split(" "));
		return workaround.needsWorkaround();
	}

	@Test
	public void testNone() {
		assertFalse(checkEnabled(""));
	}

	@Test
	public void testHalf1() {
		assertFalse(checkEnabled("--assetIndex"));
	}

	@Test
	public void testHalf2() {
		assertFalse(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex"));
	}

	@Test
	public void testAmbiguity1() {
		assertFalse(checkEnabled("--username --assetIndex"));
	}

	@Test
	public void testAmbiguity2() {
		assertTrue(checkEnabled("--demo --assetIndex 1.12"));
	}

	@Test
	public void test1_7_9() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.7.4 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userProperties \"{}\" --userType mojang"));
	}

	@Test
	public void test1_7_10() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.7.10 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userProperties \"{}\" --userType mojang"));
	}

	@Test
	public void test1_8_9() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.8 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userProperties \"{}\" --userType mojang"));
	}

	@Test
	public void test1_9_4() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.9 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userType mojang --versionType release"));
	}

	@Test
	public void test1_10_2_Forge() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.10 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userType mojang --versionType Forge"));
	}

	@Test
	public void test1_11_2() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.11 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userType mojang --versionType release"));
	}

	@Test
	public void test1_12_2() {
		assertTrue(checkEnabled("--width 854 --height 480 --username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.12 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userType mojang --versionType release"));
	}

	@Test
	public void test1_13() {
		assertFalse(checkEnabled("--username character2 --version \"HMCL 3.2.SNAPSHOT\" --gameDir /home/yushijinhun/.minecraft --assetsDir /home/yushijinhun/.minecraft/assets --assetIndex 1.13 --uuid e448fdeff6394b3fac7aecc291446329 --accessToken 65a7fe601b02439fa90bea52b7d46ab5 --userType mojang --versionType release --width 854 --height 480"));
	}
}
