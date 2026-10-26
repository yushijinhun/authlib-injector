/* Owned test fixture, not a copy of Mojang authlib. */
package com.mojang.authlib.services;

import java.security.Signature;
import com.mojang.authlib.properties.Property;

public final class MinecraftServicesKeyInfo {
	public boolean validateProperty(Property property) {
		throw new AssertionError("Minecraft services key info was not transformed");
	}
	public Signature signature() {
		throw new AssertionError("Minecraft services signature factory was not transformed");
	}
}
