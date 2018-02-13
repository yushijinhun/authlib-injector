package org.to2mbn.authlibinjector;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import org.to2mbn.authlibinjector.transform.ClassTransformer;

public final class AuthlibInjector {

	private static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.",
			"com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.",
			"io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist.", "org.xml.", "org.jcp.", "paulscode.",
			"com.ibm.", "joptsimple.", "org.to2mbn.authlibinjector." };

	private AuthlibInjector() {}

	private static boolean booted = false;

	public static void log(String message, Object... args) {
		System.err.println("[authlib-injector] " + MessageFormat.format(message, args));
	}

	public static void bootstrap(Consumer<ClassFileTransformer> transformerRegistry) {
		if (booted) {
			log("already booted, skipping");
			return;
		}
		booted = true;

		Optional<InjectorConfig> optionalConfig = configure();
		if (!optionalConfig.isPresent()) {
			log("no config is found, exiting");
			return;
		}

		InjectorConfig config = optionalConfig.get();
		ClassTransformer transformer = new ClassTransformer();

		if (config.isDebug()) transformer.debug = true;

		for (String ignore : nonTransformablePackages)
			transformer.ignores.add(ignore);

		config.applyTransformers(transformer.units);
		transformerRegistry.accept(transformer);
	}

	private static Optional<InjectorConfig> configure() {
		String url = System.getProperty("org.to2mbn.authlibinjector.config");
		if (url == null) {
			return empty();
		}
		log("trying to config remotely: {0}", url);

		InjectorConfig config = new InjectorConfig();
		config.setDebug("true".equals(System.getProperty("org.to2mbn.authlibinjector.debug")));

		RemoteConfiguration remoteConfig;
		try {
			remoteConfig = RemoteConfiguration.fetch(url);
		} catch (IOException e) {
			log("unable to configure remotely: {0}", e);
			return empty();
		}

		if (config.isDebug()) {
			log("fetched remote config: {0}", remoteConfig);
		}

		remoteConfig.applyToInjectorConfig(config);
		return of(config);
	}

}
