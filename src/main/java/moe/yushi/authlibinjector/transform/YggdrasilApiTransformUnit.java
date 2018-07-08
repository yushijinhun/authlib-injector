package moe.yushi.authlibinjector.transform;

import java.util.function.Function;
import java.util.regex.Pattern;

public class YggdrasilApiTransformUnit extends RegexTransformUnit {

	// TODO: If java.util.regex supports branch reset, we can use the following regex:
	// ^https?:\/\/(?|((?<subdomain>(api|authserver|sessionserver))\.(?<domain>mojang\.com))|((?<subdomain>(skins))\.(?<domain>minecraft\.net)))(?<path>\/.*)$
	// => ${apiRoot}${subdomain}${path}
	//
	// But now it's not supported, so we use a workaround here:
	// ^https?:\/\/(?:(?:(api|authserver|sessionserver)\.(mojang\.com))|(?:(skins)\.(minecraft\.net)))(?<path>\/.*)$
	// => ${apiRoot}$1$3${path}

	public static final Pattern REGEX = Pattern.compile("^https?:\\/\\/(?:(?:(api|authserver|sessionserver)\\.(mojang\\.com))|(?:(skins)\\.(minecraft\\.net)))(?<path>\\/.*)$");
	public static final Function<String, String> REPLACEMENT = apiRoot -> apiRoot + "$1$3${path}";

	public YggdrasilApiTransformUnit(String apiRoot) {
		super(REGEX, REPLACEMENT.apply(apiRoot));
	}

	@Override
	public String toString() {
		return "yggdrasil-api-transform";
	}
}
