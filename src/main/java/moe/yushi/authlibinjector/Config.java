/*
 * Copyright (C) 2020  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector;

import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.ERROR;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Config {

	/*
	 * See readme for option details.
	 *
	 * Deprecated options:
	 *   -Dauthlibinjector.debug=all
	 *      replaced by -Dauthlibinjector.debug
	 *   -Dauthlibinjector.mojang.proxy=...
	 *     replaced by -Dauthlibinjector.mojangProxy=...
	 */

	private Config() {}

	public static enum FeatureOption {
		DEFAULT, ENABLED, DISABLED;
		public boolean isEnabled(boolean defaultValue) {
			return this == DEFAULT ? defaultValue : this == ENABLED;
		}
	}

	public static boolean verboseLogging;
	public static boolean authlibLogging;
	public static boolean printUntransformedClass;
	public static boolean dumpClass;
	public static boolean httpdDisabled;
	public static /* nullable */ Proxy mojangProxy;
	public static Set<String> ignoredPackages;
	public static FeatureOption mojangNamespace;
	public static FeatureOption legacySkinPolyfill;

	private static void initDebugOptions() {
		String prop = System.getProperty("authlibinjector.debug");
		if ("all".equals(prop)) {
			prop = "";
			log(WARNING, "'-Dauthlibinjector.debug=all' is deprecated, use '-Dauthlibinjector.debug' instead");
		}
		if (prop == null) {
			// all disabled if param not specified
		} else if (prop.isEmpty()) {
			verboseLogging = true;
			authlibLogging = true;
		} else {
			for (String option : prop.split(",")) {
				switch (option) {
					case "verbose":
						verboseLogging = true;
						break;
					case "authlib":
						authlibLogging = true;
						break;
					case "printUntransformed":
						printUntransformedClass = true;
						verboseLogging = true;
						break;
					case "dumpClass":
						dumpClass = true;
						break;
					default:
						log(ERROR, "Unrecognized debug option: " + option);
						throw new InitializationException();
				}
			}
		}
	}

	private static final String[] DEFAULT_IGNORED_PACKAGES = {
			"moe.yushi.authlibinjector.",
			"java.",
			"javax.",
			"jdk.",
			"com.sun.",
			"sun.",
			"net.java.",

			"com.google.",
			"com.ibm.",
			"com.jcraft.jogg.",
			"com.jcraft.jorbis.",
			"com.oracle.",
			"com.paulscode.",

			"org.GNOME.",
			"org.apache.",
			"org.graalvm.",
			"org.jcp.",
			"org.json.",
			"org.lwjgl.",
			"org.objectweb.asm.",
			"org.w3c.",
			"org.xml.",
			"org.yaml.snakeyaml.",

			"gnu.trove.",
			"io.netty.",
			"it.unimi.dsi.fastutil.",
			"javassist.",
			"jline.",
			"joptsimple.",
			"oracle.",
			"oshi.",
			"paulscode.",
	};

	private static void initIgnoredPackages() {
		Set<String> pkgs = new HashSet<>();
		for (String pkg : DEFAULT_IGNORED_PACKAGES)
			pkgs.add(pkg);
		String propIgnoredPkgs = System.getProperty("authlibinjector.ignoredPackages");
		if (propIgnoredPkgs != null) {
			for (String pkg : propIgnoredPkgs.split(",")) {
				pkg = pkg.trim();
				if (!pkg.isEmpty())
					pkgs.add(pkg);
			}
		}
		ignoredPackages = Collections.unmodifiableSet(pkgs);
	}

	private static void initMojangProxy() {
		String prop = System.getProperty("authlibinjector.mojangProxy");
		if (prop == null) {
			prop = System.getProperty("authlibinjector.mojang.proxy");
			if (prop == null) {
				return;
			} else {
				log(WARNING, "'-Dauthlibinjector.mojang.proxy=' is deprecated, use '-Dauthlibinjector.mojangProxy=' instead");
			}
		}

		Matcher matcher = Pattern.compile("^(?<protocol>[^:]+)://(?<host>[^/]+)+:(?<port>\\d+)$").matcher(prop);
		if (!matcher.find()) {
			log(ERROR, "Unrecognized proxy URL: " + prop);
			throw new InitializationException();
		}

		String protocol = matcher.group("protocol");
		String host = matcher.group("host");
		int port = Integer.parseInt(matcher.group("port"));

		switch (protocol) {
			case "socks":
				mojangProxy = new Proxy(Type.SOCKS, new InetSocketAddress(host, port));
				break;

			default:
				log(ERROR, "Unsupported proxy protocol: " + protocol);
				throw new InitializationException();
		}
		log(INFO, "Mojang proxy: " + mojangProxy);
	}

	private static FeatureOption parseFeatureOption(String property) {
		String prop = System.getProperty(property);
		if (prop == null) {
			return FeatureOption.DEFAULT;
		}
		try {
			return FeatureOption.valueOf(prop.toUpperCase());
		} catch (IllegalArgumentException e) {
			log(ERROR, "Invalid option: " + prop);
			throw new InitializationException(e);
		}
	}

	static void init() {
		initDebugOptions();
		initIgnoredPackages();
		initMojangProxy();

		mojangNamespace = parseFeatureOption("authlibinjector.mojangNamespace");
		legacySkinPolyfill = parseFeatureOption("authlibinjector.legacySkinPolyfill");
		httpdDisabled = System.getProperty("authlibinjector.disableHttpd") != null;
	}
}
