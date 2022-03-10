/*
 * Copyright (C) 2021  Haowei Wen <yushijinhun@gmail.com> and contributors
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
import static java.util.Objects.requireNonNull;
import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.removeNewLines;
import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static moe.yushi.authlibinjector.util.Logging.Level.ERROR;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import moe.yushi.authlibinjector.httpd.DefaultURLRedirector;
import moe.yushi.authlibinjector.httpd.LegacySkinAPIFilter;
import moe.yushi.authlibinjector.httpd.AntiFeaturesFilter;
import moe.yushi.authlibinjector.httpd.QueryProfileFilter;
import moe.yushi.authlibinjector.httpd.QueryUUIDsFilter;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.httpd.URLProcessor;
import moe.yushi.authlibinjector.transform.ClassTransformer;
import moe.yushi.authlibinjector.transform.DumpClassListener;
import moe.yushi.authlibinjector.transform.support.*;
import moe.yushi.authlibinjector.yggdrasil.CustomYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.MojangYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;

public final class AuthlibInjector {
	private AuthlibInjector() {}

	private static boolean booted = false;
	private static Instrumentation instrumentation;
	private static boolean retransformSupported;
	private static ClassTransformer classTransformer;

	public static synchronized void bootstrap(Instrumentation instrumentation, String apiUrl) throws InitializationException {
		if (booted) {
			log(INFO, "Already started, skipping");
			return;
		}
		booted = true;
		AuthlibInjector.instrumentation = requireNonNull(instrumentation);
		Config.init();

		retransformSupported = instrumentation.isRetransformClassesSupported();
		if (!retransformSupported) {
			log(WARNING, "Retransform is not supported");
		}

		log(INFO, "Version: " + AuthlibInjector.class.getPackage().getImplementationVersion());

		APIMetadata apiMetadata = fetchAPIMetadata(apiUrl);
		classTransformer = createTransformer(apiMetadata);
		instrumentation.addTransformer(classTransformer, retransformSupported);

		ProxyParameterWorkaround.init();
		MC52974Workaround.init();
		MC52974_1710Workaround.init();
		if (!Config.noShowServerName) {
			AuthServerNameInjector.init(apiMetadata);
		}
	}

	private static Optional<String> getPrefetchedResponse() {
		String prefetched = System.getProperty("authlibinjector.yggdrasil.prefetched");
		if (prefetched == null) {
			prefetched = System.getProperty("org.to2mbn.authlibinjector.config.prefetched");
			if (prefetched != null) {
				log(WARNING, "'-Dorg.to2mbn.authlibinjector.config.prefetched=' is deprecated, use '-Dauthlibinjector.yggdrasil.prefetched=' instead");
			}
		}
		return Optional.ofNullable(prefetched);
	}

	private static APIMetadata fetchAPIMetadata(String apiUrl) {
		if (apiUrl == null || apiUrl.isEmpty()) {
			log(ERROR, "No authentication server specified");
			throw new InitializationException();
		}

		apiUrl = addHttpsIfMissing(apiUrl);
		log(INFO, "Authentication server: " + apiUrl);
		warnIfHttp(apiUrl);

		String metadataResponse;

		Optional<String> prefetched = getPrefetchedResponse();
		if (prefetched.isPresent()) {

			log(DEBUG, "Prefetched metadata detected");
			try {
				metadataResponse = new String(Base64.getDecoder().decode(removeNewLines(prefetched.get())), UTF_8);
			} catch (IllegalArgumentException e) {
				log(ERROR, "Unable to decode metadata: " + e + "\n"
						+ "Encoded metadata:\n"
						+ prefetched.get());
				throw new InitializationException(e);
			}

		} else {

			try {
				HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();

				String ali = connection.getHeaderField("x-authlib-injector-api-location");
				if (ali != null) {
					URL absoluteAli = new URL(connection.getURL(), ali);
					if (!urlEqualsIgnoreSlash(apiUrl, absoluteAli.toString())) {

						// usually the URL that ALI points to is on the same host
						// so the TCP connection can be reused
						// we need to consume the response to make the connection reusable
						try (InputStream in = connection.getInputStream()) {
							while (in.read() != -1)
								;
						} catch (IOException e) {
						}

						log(INFO, "Redirect to: " + absoluteAli);
						apiUrl = absoluteAli.toString();
						warnIfHttp(apiUrl);
						connection = (HttpURLConnection) absoluteAli.openConnection();
					}
				}

				try (InputStream in = connection.getInputStream()) {
					metadataResponse = asString(asBytes(in));
				}
			} catch (IOException e) {
				log(ERROR, "Failed to fetch metadata: " + e);
				throw new InitializationException(e);
			}

		}

		log(DEBUG, "Metadata: " + metadataResponse);

		if (!apiUrl.endsWith("/")) {
			apiUrl += "/";
		}

		APIMetadata metadata;
		try {
			metadata = APIMetadata.parse(apiUrl, metadataResponse);
		} catch (UncheckedIOException e) {
			log(ERROR, "Unable to parse metadata: " + e.getCause() + "\n"
					+ "Raw metadata:\n"
					+ metadataResponse);
			throw new InitializationException(e);
		}
		log(DEBUG, "Parsed metadata: " + metadata);
		return metadata;
	}

	private static void warnIfHttp(String url) {
		if (url.toLowerCase().startsWith("http://")) {
			log(WARNING, "You are using HTTP protocol, which is INSECURE! Please switch to HTTPS if possible.");
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

	private static List<URLFilter> createFilters(APIMetadata config) {
		if (Config.httpdDisabled) {
			log(INFO, "Disabled local HTTP server");
			return emptyList();
		}

		List<URLFilter> filters = new ArrayList<>();

		YggdrasilClient customClient = new YggdrasilClient(new CustomYggdrasilAPIProvider(config));
		YggdrasilClient mojangClient = new YggdrasilClient(new MojangYggdrasilAPIProvider(), Config.mojangProxy);

		boolean legacySkinPolyfillDefault = !Boolean.TRUE.equals(config.getMeta().get("feature.legacy_skin_api"));
		if (Config.legacySkinPolyfill.isEnabled(legacySkinPolyfillDefault)) {
			filters.add(new LegacySkinAPIFilter(customClient));
		} else {
			log(INFO, "Disabled legacy skin API polyfill");
		}

		boolean mojangNamespaceDefault = !Boolean.TRUE.equals(config.getMeta().get("feature.no_mojang_namespace"));
		if (Config.mojangNamespace.isEnabled(mojangNamespaceDefault)) {
			filters.add(new QueryUUIDsFilter(mojangClient, customClient));
			filters.add(new QueryProfileFilter(mojangClient, customClient));
		} else {
			log(INFO, "Disabled Mojang namespace");
		}

		filters.add(new AntiFeaturesFilter());

		return filters;
	}

	private static ClassTransformer createTransformer(APIMetadata config) {
		URLProcessor urlProcessor = new URLProcessor(createFilters(config), new DefaultURLRedirector(config));

		ClassTransformer transformer = new ClassTransformer();
		transformer.ignores.addAll(Config.ignoredPackages);

		if (Config.dumpClass) {
			transformer.listeners.add(new DumpClassListener(Paths.get("").toAbsolutePath()));
		}

		if (Config.authlibLogging) {
			transformer.units.add(new AuthlibLogInterceptor());
		}

		transformer.units.add(new MainArgumentsTransformer());
		transformer.units.add(new ConstantURLTransformUnit(urlProcessor));
		transformer.units.add(new CitizensTransformer());
		transformer.units.add(new ConcatenateURLTransformUnit());

		transformer.units.add(new SkinWhitelistTransformUnit());
		SkinWhitelistTransformUnit.getWhitelistedDomains().addAll(config.getSkinDomains());

		transformer.units.add(new YggdrasilKeyTransformUnit());

		transformer.units.add(new BungeeCordTransformer());

		config.getDecodedPublickey().ifPresent(YggdrasilKeyTransformUnit.PUBLIC_KEYS::add);

		return transformer;
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
			log(INFO, "Attempt to retransform classes: " + Arrays.toString(classes));
			try {
				instrumentation.retransformClasses(classes);
			} catch (Throwable e) {
				log(WARNING, "Failed to retransform", e);
			}
		}
	}

	public static void retransformAllClasses() {
		if (!retransformSupported) {
			return;
		}
		log(INFO, "Attempt to retransform all classes");
		long t0 = System.currentTimeMillis();

		Class<?>[] classes = Stream.of(instrumentation.getAllLoadedClasses())
				.filter(AuthlibInjector::canRetransformClass)
				.toArray(Class[]::new);
		if (classes.length > 0) {
			try {
				instrumentation.retransformClasses(classes);
			} catch (Throwable e) {
				log(WARNING, "Failed to retransform", e);
				return;
			}
		}

		long t1 = System.currentTimeMillis();
		log(INFO, "Retransformed " + classes.length + " classes in " + (t1 - t0) + "ms");
	}

	private static boolean canRetransformClass(Class<?> clazz) {
		if (!instrumentation.isModifiableClass(clazz)) {
			return false;
		}
		String name = clazz.getName();
		for (String prefix : Config.ignoredPackages) {
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
