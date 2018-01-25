package org.to2mbn.authlibinjector;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.instrument.ClassFileTransformer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.function.Consumer;
import org.to2mbn.authlibinjector.internal.org.json.JSONException;
import org.to2mbn.authlibinjector.internal.org.json.JSONObject;
import org.to2mbn.authlibinjector.internal.org.json.JSONTokener;
import org.to2mbn.authlibinjector.transform.ClassTransformer;
import org.yaml.snakeyaml.Yaml;

public final class AuthlibInjector {

	private static final String[] nonTransformablePackages = new String[] { "java.", "javax.", "com.sun.",
			"com.oracle.", "jdk.", "sun.", "org.apache.", "com.google.", "oracle.", "com.oracle.", "com.paulscode.",
			"io.netty.", "org.lwjgl.", "net.java.", "org.w3c.", "javassist.", "org.xml." };

	private AuthlibInjector() {}

	public static void log(String message, Object... args) {
		System.err.println("[authlib-injector] " + MessageFormat.format(message, args));
	}

	public static void bootstrap(Consumer<ClassFileTransformer> transformerRegistry) {
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
		// 1. remote config
		Optional<InjectorConfig> localConfig = loadLocalConfig();
		if (localConfig.isPresent()) return localConfig;

		// 2. local config
		Optional<InjectorConfig> remoteConfig = loadRemoteConfig();
		if (remoteConfig.isPresent()) return remoteConfig;

		return empty();
	}

	private static Optional<InjectorConfig> loadLocalConfig() {
		try {
			Optional<InputStream> localConfig = locateLocalConfig();
			if (localConfig.isPresent()) {
				try (Reader reader = new InputStreamReader(localConfig.get(), StandardCharsets.UTF_8)) {
					return of(new Yaml().loadAs(reader, InjectorConfig.class));
				}
			}
		} catch (IOException e) {
			log("unable to configure locally: {0}", e);
		}
		return empty();
	}

	private static Optional<InputStream> locateLocalConfig() throws IOException {
		// 1. the specified config file
		String configProperty = System.getProperty("org.to2mbn.authlibinjector.config");
		if (configProperty != null && !configProperty.startsWith("@")) {
			Path configFile = Paths.get(configProperty);
			if (Files.exists(configFile)) {
				log("using config: " + configProperty);
				return of(Files.newInputStream(configFile));
			} else {
				log("file not exists: {0}", configProperty);
			}
		}

		// 2. the config file in jar
		InputStream packedConfig = AuthlibInjector.class.getResourceAsStream("/authlib-injector.yaml");
		if (packedConfig != null) {
			log("using config: jar:/authlib-injector.yaml");
			return of(packedConfig);
		}

		// 3. the config in the current dir
		Path currentConfigFile = Paths.get("authlib-injector.yaml");
		if (Files.exists(currentConfigFile)) {
			log("using config: ./authlib-injector.yaml");
			return of(Files.newInputStream(currentConfigFile));
		}

		return empty();
	}

	private static Optional<InjectorConfig> loadRemoteConfig() {
		String configProperty = System.getProperty("org.to2mbn.authlibinjector.config");
		if (configProperty == null || !configProperty.startsWith("@")) {
			return empty();
		}
		String url = configProperty.substring(1);
		log("trying to config remotely: {0}", url);

		JSONObject remoteConfig;
		try {
			remoteConfig = jsonGet(url);
		} catch (IOException e) {
			log("unable to configure remotely: {0}", e);
			return empty();
		}

		InjectorConfig config = new InjectorConfig();
		config.setApiRoot(url.endsWith("/") ? url : url + "/");
		config.setDebug("true".equals(System.getProperty("org.to2mbn.authlibinjector.remoteconfig.debug")));
		config.readFromJson(remoteConfig);

		if (config.isDebug()) {
			log("fetched remote config: {0}", remoteConfig);
		}

		return of(config);
	}

	private static JSONObject jsonGet(String url) throws IOException {
		try (Reader reader = new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8)) {
			return new JSONObject(new JSONTokener(reader));
		} catch (JSONException e) {
			throw new IOException("Unresolvable JSON", e);
		}
	}

}
