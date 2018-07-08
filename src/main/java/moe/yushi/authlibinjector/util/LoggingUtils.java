package moe.yushi.authlibinjector.util;

import static moe.yushi.authlibinjector.AuthlibInjector.PROP_DEBUG;
import java.text.MessageFormat;

public final class LoggingUtils {

	private static boolean debug = "true".equals(System.getProperty(PROP_DEBUG));

	public static void info(String message, Object... args) {
		System.err.println("[authlib-injector] " + MessageFormat.format(message, args));
	}

	public static void debug(String message, Object... args) {
		if (debug) {
			info(message, args);
		}
	}

	public static boolean isDebugOn() {
		return debug;
	}

	private LoggingUtils() {}
}
