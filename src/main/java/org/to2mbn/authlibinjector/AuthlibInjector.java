package org.to2mbn.authlibinjector;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.function.Consumer;
import org.to2mbn.authlibinjector.transform.ClassTransformer;
import org.yaml.snakeyaml.Yaml;

public final class AuthlibInjector {

	private static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.", "com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.", "io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist." };

	private AuthlibInjector() {}

	public static void log(String message, Object... args) {
		System.err.println("[authlib-injector] " + MessageFormat.format(message, args));
	}

	public static void bootstrap(Consumer<ClassFileTransformer> transformerRegistry) {
		InjectorConfig config = loadConfig();
		ClassTransformer transformer = new ClassTransformer();

		if (config.isDebug()) transformer.debug = true;

		for (String ignore : nonTransformablePackages)
			transformer.ignores.add(ignore);

		config.applyTransformers(transformer.units);
		transformerRegistry.accept(transformer);
	}

	private static InjectorConfig loadConfig() {
		try (Reader reader = new InputStreamReader(lookupConfig(), StandardCharsets.UTF_8)) {
			Yaml yaml = new Yaml();
			return yaml.loadAs(reader, InjectorConfig.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static InputStream lookupConfig() throws IOException {
		String configProperty = System.getProperty("org.to2mbn.authlibinjector.config");
		if (configProperty != null) {
			Path configFile = Paths.get(configProperty);
			if (!Files.exists(configFile)) {
				log("file not exists: {0}", configProperty);
			} else {
				log("using config: " + configProperty);
				return Files.newInputStream(configFile);
			}
		}

		InputStream packedConfig = AuthlibInjector.class.getResourceAsStream("/authlib-injector.yaml");
		if (packedConfig != null) {
			log("using config: jar:/authlib-injector.yaml");
			return packedConfig;
		}

		Path currentConfigFile = Paths.get("authlib-injector.yaml");
		if (!Files.exists(currentConfigFile)) {
			throw new FileNotFoundException("no config is found");
		} else {
			log("using config: ./authlib-injector.yaml");
			return Files.newInputStream(currentConfigFile);
		}
	}

}
