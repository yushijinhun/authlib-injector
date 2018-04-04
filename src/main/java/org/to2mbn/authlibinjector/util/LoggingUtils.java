package org.to2mbn.authlibinjector.util;

import java.text.MessageFormat;

public final class LoggingUtils {

	private static boolean debug = "true".equals(System.getProperty("org.to2mbn.authlibinjector.debug"));

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
