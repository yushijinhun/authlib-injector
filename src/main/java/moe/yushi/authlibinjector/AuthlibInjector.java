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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.removeNewLines;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.Proxy.Type;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import moe.yushi.authlibinjector.httpd.DefaultURLRedirector;
import moe.yushi.authlibinjector.httpd.LegacySkinAPIFilter;
import moe.yushi.authlibinjector.httpd.QueryProfileFilter;
import moe.yushi.authlibinjector.httpd.QueryUUIDsFilter;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.httpd.URLProcessor;
import moe.yushi.authlibinjector.transform.ClassTransformer;
import moe.yushi.authlibinjector.transform.DumpClassListener;
import moe.yushi.authlibinjector.transform.support.AuthlibLogInterceptor;
import moe.yushi.authlibinjector.transform.support.CitizensTransformer;
import moe.yushi.authlibinjector.transform.support.ConstantURLTransformUnit;
import moe.yushi.authlibinjector.transform.support.MC52974Workaround;
import moe.yushi.authlibinjector.transform.support.MC52974_1710Workaround;
import moe.yushi.authlibinjector.transform.support.MainArgumentsTransformer;
import moe.yushi.authlibinjector.transform.support.SkinWhitelistTransformUnit;
import moe.yushi.authlibinjector.transform.support.YggdrasilKeyTransformUnit;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.CustomYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.MojangYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;

public final class AuthlibInjector {

	// ==== System Properties ===

	/**
	 * Stores the API root, should be set before {@link #bootstrap(Consumer)} is invoked.
	 */
	public static final String PROP_API_ROOT = "authlibinjector.yggdrasil";

	/**
	 * Stores the prefetched API root response, should be set by the launcher.
	 */
	public static final String PROP_PREFETCHED_DATA = "authlibinjector.yggdrasil.prefetched";

	/**
	 * @see #PROP_PREFETCHED_DATA
	 */
	public static final String PROP_PREFETCHED_DATA_OLD = "org.to2mbn.authlibinjector.config.prefetched";

	/**
	 * The name of loggers to have debug level turned on.
	 */
	public static final String PROP_DEBUG = "authlibinjector.debug";

	/**
	 * Whether to save modified classes for debugging.
	 */
	public static final String PROP_DUMP_CLASS = "authlibinjector.dumpClass";

	/**
	 * Whether to print the classes that are bytecode-analyzed but not transformed.
	 */
	public static final String PROP_PRINT_UNTRANSFORMED_CLASSES = "authlibinjector.printUntransformed";

	/**
	 * The proxy to use when accessing Mojang's APIs.
	 */
	public static final String PROP_MOJANG_PROXY = "authlibinjector.mojang.proxy";

	/**
	 * Additional packages to ignore.
	 */
	public static final String PROP_IGNORED_PACKAGES = "authlibinjector.ignoredPackages";

	public static final String PROP_DISABLE_HTTPD = "authlibinjector.httpd.disable";

	// ====

	// ==== Package filtering ====
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

	public static final Set<String> ignoredPackages;

	static {
		Set<String> pkgs = new HashSet<>();
		for (String pkg : DEFAULT_IGNORED_PACKAGES) {
			pkgs.add(pkg);
		}

		String propIgnoredPkgs = System.getProperty(PROP_IGNORED_PACKAGES);
		if (propIgnoredPkgs != null) {
			for (String pkg : propIgnoredPkgs.split(",")) {
				pkg = pkg.trim();
				if (!pkg.isEmpty()) {
					pkgs.add(pkg);
				}
			}
		}

		ignoredPackages = Collections.unmodifiableSet(pkgs);
	}
	// ====

	private AuthlibInjector() {}

	private static boolean booted = false;
	private static Instrumentation instrumentation;
	private static boolean retransformSupported;
	private static ClassTransformer classTransformer;

	public static synchronized void bootstrap(Instrumentation instrumentation) throws InjectorInitializationException {
		if (booted) {
			Logging.LAUNCH.info("Already started, skipping");
			return;
		}
		booted = true;
		AuthlibInjector.instrumentation = instrumentation;

		retransformSupported = instrumentation.isRetransformClassesSupported();
		if (!retransformSupported) {
			Logging.LAUNCH.warning("Retransform is not supported");
		}

		Logging.LAUNCH.info("Version: " + getVersion());

		Optional<YggdrasilConfiguration> optionalConfig = configure();
		if (optionalConfig.isPresent()) {
			classTransformer = createTransformer(optionalConfig.get());
			instrumentation.addTransformer(classTransformer, retransformSupported);

			MC52974Workaround.init();
			MC52974_1710Workaround.init();
		} else {
			Logging.LAUNCH.severe("No config available");
			throw new InjectorInitializationException();
		}
	}

	private static Optional<String> getPrefetchedResponse() {
		String prefetched = System.getProperty(PROP_PREFETCHED_DATA);
		if (prefetched == null) {
			prefetched = System.getProperty(PROP_PREFETCHED_DATA_OLD);
			if (prefetched != null) {
				Logging.LAUNCH.warning(PROP_PREFETCHED_DATA_OLD + " option is deprecated, please use " + PROP_PREFETCHED_DATA + " instead");
			}
		}
		return Optional.ofNullable(prefetched);
	}

	private static Optional<YggdrasilConfiguration> configure() {
		String apiRoot = System.getProperty(PROP_API_ROOT);
		if (apiRoot == null) return empty();

		apiRoot = addHttpsIfMissing(apiRoot);
		Logging.CONFIG.info("API root: " + apiRoot);
		warnIfHttp(apiRoot);

		String metadataResponse;

		Optional<String> prefetched = getPrefetchedResponse();
		if (!prefetched.isPresent()) {

			try {
				HttpURLConnection connection = (HttpURLConnection) new URL(apiRoot).openConnection();

				String ali = connection.getHeaderField("x-authlib-injector-api-location");
				if (ali != null) {
					URL absoluteAli = new URL(connection.getURL(), ali);
					if (!urlEqualsIgnoreSlash(apiRoot, absoluteAli.toString())) {

						// usually the URL that ALI points to is on the same host
						// so the TCP connection can be reused
						// we need to consume the response to make the connection reusable
						try (InputStream in = connection.getInputStream()) {
							while (in.read() != -1)
								;
						} catch (IOException e) {
						}

						Logging.CONFIG.info("Redirect to: " + absoluteAli);
						apiRoot = absoluteAli.toString();
						warnIfHttp(apiRoot);
						connection = (HttpURLConnection) absoluteAli.openConnection();
					}
				}

				try (InputStream in = connection.getInputStream()) {
					metadataResponse = asString(asBytes(in));
				}
			} catch (IOException e) {
				Logging.CONFIG.severe("Failed to fetch metadata: " + e);
				throw new InjectorInitializationException(e);
			}

		} else {
			Logging.CONFIG.info("Prefetched metadata detected");
			try {
				metadataResponse = new String(Base64.getDecoder().decode(removeNewLines(prefetched.get())), UTF_8);
			} catch (IllegalArgumentException e) {
				Logging.CONFIG.severe("Unable to decode metadata: " + e + "\n"
						+ "Encoded metadata:\n"
						+ prefetched.get());
				throw new InjectorInitializationException(e);
			}
		}

		Logging.CONFIG.fine("Metadata: " + metadataResponse);

		if (!apiRoot.endsWith("/"))
			apiRoot += "/";

		YggdrasilConfiguration configuration;
		try {
			configuration = YggdrasilConfiguration.parse(apiRoot, metadataResponse);
		} catch (UncheckedIOException e) {
			Logging.CONFIG.severe("Unable to parse metadata: " + e.getCause() + "\n"
					+ "Raw metadata:\n"
					+ metadataResponse);
			throw new InjectorInitializationException(e);
		}
		Logging.CONFIG.fine("Parsed metadata: " + configuration);
		return of(configuration);
	}

	private static void warnIfHttp(String url) {
		if (url.toLowerCase().startsWith("http://")) {
			Logging.CONFIG.warning("You are using HTTP protocol, which is INSECURE! Please switch to HTTPS if possible.");
		}
	}

	private static String addHttpsIfMissing(String url) {
		String lowercased = url.toLowerCase();
		if (!lowercased.startsWith("http://") && !lowercased.startsWith("https://")) {
			url = "https://" + url;
		}
		return url;
	}

	private static boolean urlEqualsIgnoreSlash(String a, String b) {
		if (!a.endsWith("/"))
			a += "/";
		if (!b.endsWith("/"))
			b += "/";
		return a.equals(b);
	}

	private static List<URLFilter> createFilters(YggdrasilConfiguration config) {
		if (Boolean.getBoolean(PROP_DISABLE_HTTPD)) {
			return emptyList();
		}

		List<URLFilter> filters = new ArrayList<>();

		YggdrasilClient customClient = new YggdrasilClient(new CustomYggdrasilAPIProvider(config));
		YggdrasilClient mojangClient = new YggdrasilClient(new MojangYggdrasilAPIProvider(), getMojangProxy());

		if (Boolean.TRUE.equals(config.getMeta().get("feature.legacy_skin_api"))) {
			Logging.CONFIG.info("Disabled local redirect for legacy skin API, as the remote Yggdrasil server supports it");
		} else {
			filters.add(new LegacySkinAPIFilter(customClient));
		}

		filters.add(new QueryUUIDsFilter(mojangClient, customClient));
		filters.add(new QueryProfileFilter(mojangClient, customClient));

		return filters;
	}

	private static Proxy getMojangProxy() {
		String proxyString = System.getProperty(PROP_MOJANG_PROXY);
		if (proxyString == null) {
			return null;
		}
		Matcher matcher = Pattern.compile("^(?<protocol>[^:]+)://(?<host>[^/]+)+:(?<port>\\d+)$").matcher(proxyString);
		if (!matcher.find()) {
			Logging.LAUNCH.severe("Failed to parse proxy string: " + proxyString);
			throw new InjectorInitializationException();
		}

		String protocol = matcher.group("protocol");
		String host = matcher.group("host");
		int port = Integer.parseInt(matcher.group("port"));

		Proxy proxy;
		switch (protocol) {
			case "socks":
				proxy = new Proxy(Type.SOCKS, new InetSocketAddress(host, port));
				break;

			default:
				Logging.LAUNCH.severe("Unsupported proxy protocol: " + protocol);
				throw new InjectorInitializationException();
		}
		Logging.LAUNCH.info("Mojang proxy set: " + proxy);
		return proxy;
	}

	private static ClassTransformer createTransformer(YggdrasilConfiguration config) {
		URLProcessor urlProcessor = new URLProcessor(createFilters(config), new DefaultURLRedirector(config));

		ClassTransformer transformer = new ClassTransformer();
		transformer.ignores.addAll(ignoredPackages);

		if ("true".equals(System.getProperty(PROP_DUMP_CLASS))) {
			transformer.listeners.add(new DumpClassListener(Paths.get("").toAbsolutePath()));
		}

		if (Logging.isDebugOnFor(Logging.PREFIX + ".authlib")) {
			transformer.units.add(new AuthlibLogInterceptor());
		}

		transformer.units.add(new MainArgumentsTransformer());
		transformer.units.add(new ConstantURLTransformUnit(urlProcessor));
		transformer.units.add(new CitizensTransformer());

		transformer.units.add(new SkinWhitelistTransformUnit());
		SkinWhitelistTransformUnit.getWhitelistedDomains().addAll(config.getSkinDomains());

		transformer.units.add(new YggdrasilKeyTransformUnit());
		config.getDecodedPublickey().ifPresent(YggdrasilKeyTransformUnit.PUBLIC_KEYS::add);

		return transformer;
	}

	public static String getVersion() {
		return AuthlibInjector.class.getPackage().getImplementationVersion();
	}

	public static void retransformClasses(String... classNames) {
		if (!retransformSupported) {
			return;
		}
		Set<String> classNamesSet = new HashSet<>(Arrays.asList(classNames));
		Class<?>[] classes = Stream.of(instrumentation.getAllLoadedClasses())
				.filter(clazz -> classNamesSet.contains(clazz.getName()))
				.filter(AuthlibInjector::canRetransformClass)
				.toArray(Class[]::new);
		if (classes.length > 0) {
			Logging.TRANSFORM.info("Attempt to retransform classes: " + Arrays.toString(classes));
			try {
				instrumentation.retransformClasses(classes);
			} catch (Throwable e) {
				Logging.TRANSFORM.log(Level.WARNING, "Failed to retransform", e);
			}
		}
	}

	public static void retransformAllClasses() {
		if (!retransformSupported) {
			return;
		}
		Logging.TRANSFORM.info("Attempt to retransform all classes");
		long t0 = System.currentTimeMillis();

		Class<?>[] classes = Stream.of(instrumentation.getAllLoadedClasses())
				.filter(AuthlibInjector::canRetransformClass)
				.toArray(Class[]::new);
		if (classes.length > 0) {
			try {
				instrumentation.retransformClasses(classes);
			} catch (Throwable e) {
				Logging.TRANSFORM.log(Level.WARNING, "Failed to retransform", e);
				return;
			}
		}

		long t1 = System.currentTimeMillis();
		Logging.TRANSFORM.info("Retransformed " + classes.length + " classes in " + (t1 - t0) + "ms");
	}

	private static boolean canRetransformClass(Class<?> clazz) {
		if (!instrumentation.isModifiableClass(clazz)) {
			return false;
		}
		String name = clazz.getName();
		for (String prefix : ignoredPackages) {
			if (name.startsWith(prefix)) {
				return false;
			}
		}
		return true;
	}

	public static ClassTransformer getClassTransformer() {
		return classTransformer;
	}
}
