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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.junit.Test;

import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.httpd.DefaultURLRedirector;

public class DefaultURLRedirectorTest {

	private String apiRoot = "https://yggdrasil.example.com/";
	private DefaultURLRedirector redirector = new DefaultURLRedirector(new YggdrasilConfiguration(apiRoot, emptyList(), emptyMap(), Optional.empty()));

	private void testTransform(String domain, String path, String output) {
		assertEquals(redirector.redirect(domain, path).get(), output);
	}

	@Test
	public void testReplace() {
		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilGameProfileRepository
				"api.mojang.com", "/profiles/",
				"https://yggdrasil.example.com/api/profiles/");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
				"sessionserver.mojang.com", "/session/minecraft/join",
				"https://yggdrasil.example.com/sessionserver/session/minecraft/join");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService
				"sessionserver.mojang.com", "/session/minecraft/hasJoined",
				"https://yggdrasil.example.com/sessionserver/session/minecraft/hasJoined");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"authserver.mojang.com", "/authenticate",
				"https://yggdrasil.example.com/authserver/authenticate");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"authserver.mojang.com", "/refresh",
				"https://yggdrasil.example.com/authserver/refresh");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"authserver.mojang.com", "/validate",
				"https://yggdrasil.example.com/authserver/validate");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"authserver.mojang.com", "/invalidate",
				"https://yggdrasil.example.com/authserver/invalidate");

		testTransform(
				// from: [com.mojang:authlib:1.5.24]/com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication
				"authserver.mojang.com", "/signout",
				"https://yggdrasil.example.com/authserver/signout");

		testTransform(
				// from: [mcp940]/net.minecraft.client.entity.AbstractClientPlayer
				// issue: yushijinhun/authlib-injector#7 <https://github.com/yushijinhun/authlib-injector/issues/7>
				"skins.minecraft.net", "/MinecraftSkins/%s.png",
				"https://yggdrasil.example.com/skins/MinecraftSkins/%s.png");

		testTransform(
				// from: [bungeecord@806a6dfacaadb7538860889f8a50612bb496a2d3]/net.md_5.bungee.connection.InitialHandler
				// url: https://github.com/SpigotMC/BungeeCord/blob/806a6dfacaadb7538860889f8a50612bb496a2d3/proxy/src/main/java/net/md_5/bungee/connection/InitialHandler.java#L409
				"sessionserver.mojang.com", "/session/minecraft/hasJoined?username=",
				"https://yggdrasil.example.com/sessionserver/session/minecraft/hasJoined?username=");

		testTransform(
				// from: [wiki.vg]/Mojang_API/Username -> UUID at time
				// url: http://wiki.vg/Mojang_API#Username_-.3E_UUID_at_time
				// issue: yushijinhun/authlib-injector#6 <https://github.com/yushijinhun/authlib-injector/issues/6>
				"api.mojang.com", "/users/profiles/minecraft/",
				"https://yggdrasil.example.com/api/users/profiles/minecraft/");
	}

	@Test
	public void testEmpty() {
		assertEquals(redirector.redirect("example.com", "/path"), Optional.empty());
	}

}
