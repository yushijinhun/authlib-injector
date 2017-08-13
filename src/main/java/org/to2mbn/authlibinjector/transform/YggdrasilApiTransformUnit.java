package org.to2mbn.authlibinjector.transform;

import java.util.regex.Pattern;

public class YggdrasilApiTransformUnit extends RegexTransformUnit {

	public YggdrasilApiTransformUnit(String apiRoot) {
		// ^https:\/\/(api|authserver|sessionserver)\.mojang\.com\/(.*)$
		super(Pattern.compile("^https:\\/\\/(api|authserver|sessionserver)\\.mojang\\.com\\/(.*)$"), apiRoot + "$1/$2");
	}

	@Override
	public String toString() {
		return "yggdrasil-api-transform";
	}
}
