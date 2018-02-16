package org.to2mbn.authlibinjector;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.to2mbn.authlibinjector.util.IOUtils.readURL;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import org.to2mbn.authlibinjector.transform.ClassTransformer;
import org.to2mbn.authlibinjector.transform.SkinWhitelistTransformUnit;
import org.to2mbn.authlibinjector.transform.YggdrasilApiTransformUnit;
import org.to2mbn.authlibinjector.transform.YggdrasilKeyTransformUnit;

public final class AuthlibInjector {

	private static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.",
			"com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.",
			"io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist.", "org.xml.", "org.jcp.", "paulscode.",
			"com.ibm.", "joptsimple.", "org.to2mbn.authlibinjector." };

	private AuthlibInjector() {}

	private static boolean booted = false;
	private static boolean debug = "true".equals(System.getProperty("org.to2mbn.authlibinjector.debug"));

	public static void info(String message, Object... args) {
		System.err.println("[authlib-injector] " + MessageFormat.format(message, args));
	}

	public static void debug(String message, Object... args) {
		if (debug) {
			info(message, args);
		}
	}

	public static void bootstrap(Consumer<ClassFileTransformer> transformerRegistry) {
		if (booted) {
			info("already booted, skipping");
			return;
		}
		booted = true;

		Optional<YggdrasilConfiguration> optionalConfig = configure();
		if (!optionalConfig.isPresent()) {
			info("no config available");
			return;
		}

		transformerRegistry.accept(createTransformer(optionalConfig.get()));
	}

	private static Optional<YggdrasilConfiguration> configure() {
		String apiRoot = System.getProperty("org.to2mbn.authlibinjector.config");
		if (apiRoot == null) return empty();
		info("api root: {0}", apiRoot);

		String metadataResponse = System.getProperty("org.to2mbn.authlibinjector.config.prefetched");

		if (metadataResponse == null) {
			info("fetching metadata");
			try {
				metadataResponse = readURL(apiRoot);
			} catch (IOException e) {
				info("unable to fetch metadata: {0}", e);
				return empty();
			}

		} else {
			info("prefetched metadata detected");
		}

		debug("metadata: {0}", metadataResponse);

		YggdrasilConfiguration configuration;
		try {
			configuration = YggdrasilConfiguration.parse(apiRoot, metadataResponse);
		} catch (IOException e) {
			info("unable to parse metadata: {0}\n"
					+ "metadata to parse:\n"
					+ "{1}",
					e, metadataResponse);
			return empty();
		}
		debug("parsed metadata: {0}", configuration);
		return of(configuration);
	}

	private static ClassTransformer createTransformer(YggdrasilConfiguration config) {
		ClassTransformer transformer = new ClassTransformer();
		transformer.debugSaveClass = debug;
		for (String ignore : nonTransformablePackages)
			transformer.ignores.add(ignore);

		transformer.units.add(new YggdrasilApiTransformUnit(config.getApiRoot()));
		transformer.units.add(new SkinWhitelistTransformUnit(config.getSkinDomains().toArray(new String[0])));
		config.getDecodedPublickey().ifPresent(
				key -> transformer.units.add(new YggdrasilKeyTransformUnit(key.getEncoded())));

		return transformer;
	}

}
