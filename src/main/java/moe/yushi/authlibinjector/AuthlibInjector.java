package moe.yushi.authlibinjector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.getURL;
import static moe.yushi.authlibinjector.util.IOUtils.removeNewLines;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import moe.yushi.authlibinjector.httpd.DeprecatedApiHandle;
import moe.yushi.authlibinjector.transform.ClassTransformer;
import moe.yushi.authlibinjector.transform.SkinWhitelistTransformUnit;
import moe.yushi.authlibinjector.transform.YggdrasilApiTransformUnit;
import moe.yushi.authlibinjector.transform.YggdrasilKeyTransformUnit;
import moe.yushi.authlibinjector.util.Logging;

public final class AuthlibInjector {

	public static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.",
			"com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.",
			"io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist.", "org.xml.", "org.jcp.", "paulscode.",
			"com.ibm.", "joptsimple.", "moe.yushi.authlibinjector.", "org.graalvm.", "org.GNOME.", "it.unimi.dsi.fastutil.",
			"oshi." };

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
	 * Whether to disable the local httpd server.
	 */
	public static final String PROP_DISABLE_HTTPD = "authlibinjector.httpd.disable";

	/**
	 * Whether to turn on debug logging.
	 */
	public static final String PROP_DEBUG = "authlibinjector.debug";

	// ====

	private AuthlibInjector() {}

	private static AtomicBoolean booted = new AtomicBoolean(false);

	public static void bootstrap(Consumer<ClassFileTransformer> transformerRegistry) {
		if (!booted.compareAndSet(false, true)) {
			Logging.ROOT.info("already booted, skipping");
			return;
		}

		Logging.ROOT.info("version: " + getVersion());

		Optional<YggdrasilConfiguration> optionalConfig = configure();
		if (optionalConfig.isPresent()) {
			transformerRegistry.accept(createTransformer(optionalConfig.get()));
		} else {
			Logging.ROOT.warning("no config available");
		}
	}

	private static Optional<String> getPrefetchedResponse() {
		String prefetched = System.getProperty(PROP_PREFETCHED_DATA);
		if (prefetched == null) {
			prefetched = System.getProperty(PROP_PREFETCHED_DATA_OLD);
			if (prefetched != null) {
				Logging.ROOT.warning("org.to2mbn.authlibinjector.config.prefetched option is deprecated and will be removed in a future release.");
			}
		}
		return Optional.ofNullable(prefetched);
	}

	private static Optional<YggdrasilConfiguration> configure() {
		String apiRoot = System.getProperty(PROP_API_ROOT);
		if (apiRoot == null) return empty();
		Logging.ROOT.info("api root: " + apiRoot);

		String metadataResponse;

		Optional<String> prefetched = getPrefetchedResponse();
		if (!prefetched.isPresent()) {
			Logging.ROOT.info("fetching metadata");
			try {
				metadataResponse = asString(getURL(apiRoot));
			} catch (IOException e) {
				Logging.ROOT.severe("unable to fetch metadata: " + e);
				throw new UncheckedIOException(e);
			}

		} else {
			Logging.ROOT.info("prefetched metadata detected");
			try {
				metadataResponse = new String(Base64.getDecoder().decode(removeNewLines(prefetched.get())), UTF_8);
			} catch (IllegalArgumentException e) {
				Logging.ROOT.severe("unable to decode metadata: " + e + "\n"
						+ "metadata to decode:\n"
						+ prefetched.get());
				throw e;
			}
		}

		Logging.ROOT.fine("metadata: " + metadataResponse);

		YggdrasilConfiguration configuration;
		try {
			configuration = YggdrasilConfiguration.parse(apiRoot, metadataResponse);
		} catch (UncheckedIOException e) {
			Logging.ROOT.severe("unable to parse metadata: " + e + "\n"
					+ "metadata to parse:\n"
					+ metadataResponse);
			throw e;
		}
		Logging.ROOT.fine("parsed metadata: " + configuration);
		return of(configuration);
	}

	private static ClassTransformer createTransformer(YggdrasilConfiguration config) {
		ClassTransformer transformer = new ClassTransformer();
		transformer.debugSaveClass = false; // TODO: add an option?
		for (String ignore : nonTransformablePackages)
			transformer.ignores.add(ignore);

		if (!"true".equals(System.getProperty(PROP_DISABLE_HTTPD))) {
			transformer.units.add(DeprecatedApiHandle.createTransformUnit(config));
		}

		transformer.units.add(new YggdrasilApiTransformUnit(config.getApiRoot()));

		transformer.units.add(new SkinWhitelistTransformUnit(config.getSkinDomains().toArray(new String[0])));

		config.getDecodedPublickey().ifPresent(
				key -> transformer.units.add(new YggdrasilKeyTransformUnit(key.getEncoded())));

		return transformer;
	}

	public static String getVersion() {
		return AuthlibInjector.class.getPackage().getImplementationVersion();
	}

}
