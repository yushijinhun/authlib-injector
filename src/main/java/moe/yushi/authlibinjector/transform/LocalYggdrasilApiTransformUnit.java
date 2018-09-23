package moe.yushi.authlibinjector.transform;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalYggdrasilApiTransformUnit extends LdcTransformUnit {

	// ^https?:\/\/(skins\.minecraft\.net)(?<path>\/.*)$
	// => <localApiRoot>${path}
	public static final Pattern REGEX = Pattern.compile("^https?:\\/\\/(skins\\.minecraft\\.net)(?<path>\\/.*)$");

	public LocalYggdrasilApiTransformUnit(Supplier<String> localApiRoot) {
		super(string -> {
			Matcher matcher = REGEX.matcher(string);
			if (matcher.find()) {
				return of(matcher.replaceAll(localApiRoot.get() + "${path}"));
			} else {
				return empty();
			}
		});
	}

	@Override
	public String toString() {
		return "Local Yggdrasil API Transformer";
	}
}
