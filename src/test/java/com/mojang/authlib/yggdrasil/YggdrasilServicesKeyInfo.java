/* Owned test fixture, not a copy of Mojang authlib. */
package com.mojang.authlib.yggdrasil;

import java.security.Signature;
import com.mojang.authlib.properties.Property;

public final class YggdrasilServicesKeyInfo {
	public boolean validateProperty(Property property) {
		throw new AssertionError("Yggdrasil key info was not transformed");
	}
	public Signature signature() {
		throw new AssertionError("Yggdrasil signature factory was not transformed");
	}
}
