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
package moe.yushi.authlibinjector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.removeNewLines;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Stream;

import moe.yushi.authlibinjector.httpd.DefaultURLRedirector;
import moe.yushi.authlibinjector.httpd.LegacySkinAPIFilter;
import moe.yushi.authlibinjector.httpd.QueryProfileFilter;
import moe.yushi.authlibinjector.httpd.QueryUUIDsFilter;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.httpd.URLProcessor;
import moe.yushi.authlibinjector.transform.AuthlibLogInterceptor;
import moe.yushi.authlibinjector.transform.ClassTransformer;
import moe.yushi.authlibinjector.transform.ConstantURLTransformUnit;
import moe.yushi.authlibinjector.transform.DumpClassListener;
import moe.yushi.authlibinjector.transform.MainArgumentsTransformer;
import moe.yushi.authlibinjector.transform.SkinWhitelistTransformUnit;
import moe.yushi.authlibinjector.transform.YggdrasilKeyTransformUnit;
import moe.yushi.authlibinjector.transform.support.CitizensTransformer;
import moe.yushi.authlibinjector.transform.support.LaunchWrapperTransformer;
import moe.yushi.authlibinjector.transform.support.MC52974Workaround;
import moe.yushi.authlibinjector.transform.support.MC52974_1710Workaround;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.CustomYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.MojangYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;

public final class AuthlibInjector {

	public static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.",
			"com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.",
			"io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist.", "org.xml.", "org.jcp.", "paulscode.",
			"com.ibm.", "joptsimple.", "moe.yushi.authlibinjector.", "org.graalvm.", "org.GNOME.", "it.unimi.dsi.fastutil.",
			"oshi.", "com.jcraft.jogg.", "com.jcraft.jorbis.", "org.objectweb.asm.", "org.yaml.snakeyaml.", "gnu.trove.",
			"jline.", "org.json." };

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
	 * The side that authlib-injector runs on.
	 * Possible values: client, server.
	 */
	public static final String PROP_SIDE = "authlibinjector.side";

	public static final String PROP_DISABLE_HTTPD = "authlibinjector.httpd.disable";

	public static final String PROP_ALI_REDIRECT_LIMIT = "authlibinjector.ali.redirectLimit";

	// ====

	private static final int REDIRECT_LIMIT = Integer.getInteger(PROP_ALI_REDIRECT_LIMIT, 5);

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

		ExecutionEnvironment side = detectSide();
		Logging.LAUNCH.fine("Detected side: " + side);

		apiRoot = parseInputUrl(apiRoot);
		Logging.CONFIG.info("API root: " + apiRoot);
		warnIfHttp(apiRoot);

		String metadataResponse;

		Optional<String> prefetched = getPrefetchedResponse();
		if (!prefetched.isPresent()) {

			try {
				HttpURLConnection connection;
				boolean redirectAllowed = side == ExecutionEnvironment.SERVER;
				int redirectCount = 0;
				for (;;) {
					connection = (HttpURLConnection) new URL(apiRoot).openConnection();
					Optional<String> ali = getApiLocationIndication(connection);
					if (ali.isPresent()) {
						if (!redirectAllowed) {
							Logging.CONFIG.warning("Redirect is not allowed, ignoring ALI: " + ali.get());
							break;
						}

						connection.disconnect();

						apiRoot = ali.get();
						if (redirectCount >= REDIRECT_LIMIT) {
							Logging.CONFIG.severe("Exceeded maximum number of redirects (" + REDIRECT_LIMIT + "), refusing to redirect to: " + apiRoot);
							throw new InjectorInitializationException();
						}
						redirectCount++;
						Logging.CONFIG.info("Redirect to: " + apiRoot);
						warnIfHttp(apiRoot);
					} else {
						break;
					}
				}

				try {
					metadataResponse = asString(asBytes(connection.getInputStream()));
				} finally {
					connection.disconnect();
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

	private static String appendSuffixSlash(String url) {
		if (!url.endsWith("/")) {
			return url + "/";
		} else {
			return url;
		}
	}

	private static String parseInputUrl(String url) {
		String lowercased = url.toLowerCase();
		if (!lowercased.startsWith("http://") && !lowercased.startsWith("https://")) {
			url = "https://" + url;
		}

		url = appendSuffixSlash(url);
		return url;
	}

	private static Optional<String> getApiLocationIndication(URLConnection conn) {
		return Optional.ofNullable(conn.getHeaderFields().get("X-Authlib-Injector-API-Location"))
				.flatMap(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)))
				.flatMap(indication -> {
					String currentUrl = appendSuffixSlash(conn.getURL().toString());
					String newUrl;
					try {
						newUrl = appendSuffixSlash(new URL(conn.getURL(), indication).toString());
					} catch (MalformedURLException e) {
						Logging.CONFIG.warning("Failed to resolve absolute ALI, the header is [" + indication + "]. Ignore it.");
						return Optional.empty();
					}

					if (newUrl.equals(currentUrl)) {
						return Optional.empty();
					} else {
						return Optional.of(newUrl);
					}
				});
	}

	private static ExecutionEnvironment detectSide() {
		String specifiedSide = System.getProperty(PROP_SIDE);
		if (specifiedSide != null) {
			switch (specifiedSide) {
				case "client":
					return ExecutionEnvironment.CLIENT;
				case "server":
					return ExecutionEnvironment.SERVER;
				default:
					Logging.LAUNCH.warning("Invalid value [" + specifiedSide + "] for parameter " + PROP_SIDE + ", ignoring.");
					break;
			}
		}

		// fallback
		if (System.getProperty(PROP_PREFETCHED_DATA) != null || System.getProperty(PROP_PREFETCHED_DATA_OLD) != null) {
			Logging.LAUNCH.warning("Prefetched configuration must be used along with parameter " + PROP_SIDE);
			return ExecutionEnvironment.CLIENT;
		} else {
			return ExecutionEnvironment.SERVER;
		}
	}

	private static List<URLFilter> createFilters(YggdrasilConfiguration config) {
		if (Boolean.getBoolean(PROP_DISABLE_HTTPD)) {
			return emptyList();
		}

		List<URLFilter> filters = new ArrayList<>();

		YggdrasilClient customClient = new YggdrasilClient(new CustomYggdrasilAPIProvider(config));
		YggdrasilClient mojangClient = new YggdrasilClient(new MojangYggdrasilAPIProvider());

		if (Boolean.TRUE.equals(config.getMeta().get("feature.legacy_skin_api"))) {
			Logging.CONFIG.info("Disabled local redirect for legacy skin API, as the remote Yggdrasil server supports it");
		} else {
			filters.add(new LegacySkinAPIFilter(customClient));
		}

		filters.add(new QueryUUIDsFilter(mojangClient, customClient));
		filters.add(new QueryProfileFilter(mojangClient, customClient));

		return filters;
	}

	private static ClassTransformer createTransformer(YggdrasilConfiguration config) {
		URLProcessor urlProcessor = new URLProcessor(createFilters(config), new DefaultURLRedirector(config));

		ClassTransformer transformer = new ClassTransformer();
		for (String ignore : nonTransformablePackages) {
			transformer.ignores.add(ignore);
		}

		if ("true".equals(System.getProperty(PROP_DUMP_CLASS))) {
			transformer.listeners.add(new DumpClassListener(Paths.get("").toAbsolutePath()));
		}

		if (Logging.isDebugOnFor(Logging.PREFIX + ".authlib")) {
			transformer.units.add(new AuthlibLogInterceptor());
		}

		transformer.units.add(new MainArgumentsTransformer());
		transformer.units.add(new ConstantURLTransformUnit(urlProcessor));
		transformer.units.add(new CitizensTransformer());
		transformer.units.add(new LaunchWrapperTransformer());

		transformer.units.add(new SkinWhitelistTransformUnit(config.getSkinDomains().toArray(new String[0])));

		transformer.units.add(new YggdrasilKeyTransformUnit());
		config.getDecodedPublickey().ifPresent(YggdrasilKeyTransformUnit.getPublicKeys()::add);

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
		for (String prefix : nonTransformablePackages) {
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
