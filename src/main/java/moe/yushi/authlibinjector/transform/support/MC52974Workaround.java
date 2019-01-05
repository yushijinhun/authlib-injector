package moe.yushi.authlibinjector.transform.support;

import static java.util.Collections.unmodifiableSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import moe.yushi.authlibinjector.util.Logging;

/**
 * See <https://github.com/yushijinhun/authlib-injector/issues/30>
 */
public class MC52974Workaround {

	private static final Set<String> AFFECTED_VERSION_SERIES = unmodifiableSet(new HashSet<>(Arrays.asList(
			"1.7.4", // MC 1.7.9 uses this
			"1.7.10",
			"1.8",
			"1.9",
			"1.10",
			"1.11",
			"1.12")));

	private boolean enabled;

	public boolean needsWorkaround() {
		return enabled;
	}

	public void acceptMainArguments(String[] args) {
		parseArgument(args, "--assetIndex").ifPresent(assetIndexName -> {
			if (AFFECTED_VERSION_SERIES.contains(assetIndexName)) {
				Logging.HTTPD.info("Current version series is " + assetIndexName + ", enable MC-52974 workaround.");
				enabled = true;
			}
		});
	}

	private static Optional<String> parseArgument(String[] args, String option) {
		boolean hit = false;
		for (String arg : args) {
			if (hit) {
				if (arg.startsWith("--")) {
					// arg doesn't seem to be a value
					// maybe the previous argument is a value, but we wrongly recognized it as an option
					hit = false;
				} else {
					return Optional.of(arg);
				}
			}

			if (option.equals(arg)) {
				hit = true;
			}
		}
		return Optional.empty();
	}
}
