package org.to2mbn.authlibinjector.test;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.to2mbn.authlibinjector.transform.YggdrasilApiTransformUnit;

@RunWith(Parameterized.class)
public class UrlReplaceTest {

	private static final String apiRoot = "https://yggdrasil.example.com/";

	@Parameters
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilGameProfileRepository
				"https://api.mojang.com/profiles/",
				"https://yggdrasil.example.com/api/profiles/"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
				"https://sessionserver.mojang.com/session/minecraft/join",
				"https://yggdrasil.example.com/sessionserver/session/minecraft/join"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
				"https://sessionserver.mojang.com/session/minecraft/hasJoined",
				"https://yggdrasil.example.com/sessionserver/session/minecraft/hasJoined"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"https://authserver.mojang.com/authenticate",
				"https://yggdrasil.example.com/authserver/authenticate"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"https://authserver.mojang.com/refresh",
				"https://yggdrasil.example.com/authserver/refresh"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"https://authserver.mojang.com/validate",
				"https://yggdrasil.example.com/authserver/validate"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"https://authserver.mojang.com/invalidate",
				"https://yggdrasil.example.com/authserver/invalidate"
			},
			{
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"https://authserver.mojang.com/signout",
				"https://yggdrasil.example.com/authserver/signout"
			},
			{
				// from: [mcp940]/net.minecraft.client.entity.AbstractClientPlayer
				// issue: to2mbn/authlib-injector#7 <https://github.com/to2mbn/authlib-injector/issues/7>
				"http://skins.minecraft.net/MinecraftSkins/%s.png",
				"https://yggdrasil.example.com/skins/MinecraftSkins/%s.png"
			},
			{
				// from: [bungeecord@806a6dfacaadb7538860889f8a50612bb496a2d3]/net.md_5.bungee.connection.InitialHandler
				// url: https://github.com/SpigotMC/BungeeCord/blob/806a6dfacaadb7538860889f8a50612bb496a2d3/proxy/src/main/java/net/md_5/bungee/connection/InitialHandler.java#L409
				"https://sessionserver.mojang.com/session/minecraft/hasJoined?username=",
				"https://yggdrasil.example.com/sessionserver/session/minecraft/hasJoined?username="
			},
			{
				// from: [wiki.vg]/Mojang_API/Username -> UUID at time
				// url: http://wiki.vg/Mojang_API#Username_-.3E_UUID_at_time
				// issue: to2mbn/authlib-injector#6 <https://github.com/to2mbn/authlib-injector/issues/6>
				"https://api.mojang.com/users/profiles/minecraft/",
				"https://yggdrasil.example.com/api/users/profiles/minecraft/"
			}
		});
		// @formatter:on
	}

	@Parameter(0)
	public String input;

	@Parameter(1)
	public String output;

	@Test
	public void test() {
		assertEquals(output,
				YggdrasilApiTransformUnit.REGEX.matcher(input).replaceAll(
						YggdrasilApiTransformUnit.REPLACEMENT.apply(apiRoot)));
	}

}
