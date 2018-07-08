package moe.yushi.authlibinjector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.IOUtils.getURL;
import static moe.yushi.authlibinjector.util.IOUtils.removeNewLines;
import static moe.yushi.authlibinjector.util.LoggingUtils.debug;
import static moe.yushi.authlibinjector.util.LoggingUtils.info;
import static moe.yushi.authlibinjector.util.LoggingUtils.isDebugOn;
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

public final class AuthlibInjector {

	public static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.",
			"com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.",
			"io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist.", "org.xml.", "org.jcp.", "paulscode.",
			"com.ibm.", "joptsimple.", "moe.yushi.authlibinjector.", "org.graalvm.", "org.GNOME.", "it.unimi.dsi.fastutil.",
			"oshi." };

	private AuthlibInjector() {}

	private static AtomicBoolean booted = new AtomicBoolean(false);

	public static void bootstrap(Consumer<ClassFileTransformer> transformerRegistry) {
		if (!booted.compareAndSet(false, true)) {
			info("already booted, skipping");
			return;
		}

		info("version: " + getVersion());

		Optional<YggdrasilConfiguration> optionalConfig = configure();
		if (optionalConfig.isPresent()) {
			transformerRegistry.accept(createTransformer(optionalConfig.get()));
		} else {
			info("no config available");
		}
	}

	private static Optional<YggdrasilConfiguration> configure() {
		String apiRoot = System.getProperty("org.to2mbn.authlibinjector.config");
		if (apiRoot == null) return empty();
		info("api root: {0}", apiRoot);

		String metadataResponse;

		String prefetched = System.getProperty("org.to2mbn.authlibinjector.config.prefetched");
		if (prefetched == null) {
			info("fetching metadata");
			try {
				metadataResponse = asString(getURL(apiRoot));
			} catch (IOException e) {
				info("unable to fetch metadata: {0}", e);
				throw new UncheckedIOException(e);
			}

		} else {
			info("prefetched metadata detected");
			try {
				metadataResponse = new String(Base64.getDecoder().decode(removeNewLines(prefetched)), UTF_8);
			} catch (IllegalArgumentException e) {
				info("unable to decode metadata: {0}\n"
						+ "metadata to decode:\n"
						+ "{1}", e, prefetched);
				throw e;
			}
		}

		debug("metadata: {0}", metadataResponse);

		YggdrasilConfiguration configuration;
		try {
			configuration = YggdrasilConfiguration.parse(apiRoot, metadataResponse);
		} catch (UncheckedIOException e) {
			info("unable to parse metadata: {0}\n"
					+ "metadata to parse:\n"
					+ "{1}",
					e, metadataResponse);
			throw e;
		}
		debug("parsed metadata: {0}", configuration);
		return of(configuration);
	}

	private static ClassTransformer createTransformer(YggdrasilConfiguration config) {
		ClassTransformer transformer = new ClassTransformer();
		transformer.debugSaveClass = isDebugOn();
		for (String ignore : nonTransformablePackages)
			transformer.ignores.add(ignore);

		if (!"true".equals(System.getProperty("org.to2mbn.authlibinjector.httpd.disable"))) {
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
