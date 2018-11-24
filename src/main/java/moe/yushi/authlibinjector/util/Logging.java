package moe.yushi.authlibinjector.util;

import static moe.yushi.authlibinjector.AuthlibInjector.PROP_DEBUG;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public final class Logging {
	private Logging() {}

	public static final String PREFIX = "moe.yushi.authlibinjector";

	public static final Logger ROOT = Logger.getLogger(PREFIX);
	public static final Logger LAUNCH = Logger.getLogger(PREFIX + ".launch");
	public static final Logger CONFIG = Logger.getLogger(PREFIX + ".config");
	public static final Logger TRANSFORM = Logger.getLogger(PREFIX + ".transform");
	public static final Logger TRANSFORM_SKIPPED = Logger.getLogger(PREFIX + ".transform.skipped");
	public static final Logger HTTPD = Logger.getLogger(PREFIX + ".httpd");

	private static Predicate<String> debugLoggerNamePredicate;

	public static void init() {
		debugLoggerNamePredicate = createDebugLoggerNamePredicate();

		initRootLogger();
	}

	private static void initRootLogger() {
		ROOT.setLevel(Level.ALL);
		ROOT.setUseParentHandlers(false);
		StreamHandler handler = new StreamHandler(System.out, new Formatter() {
			private String convertLoggerName(String loggerName) {
				if (loggerName.startsWith(PREFIX)) {
					return "authlib-injector" + loggerName.substring(PREFIX.length());
				} else {
					return loggerName;
				}
			}

			@Override
			public String format(LogRecord record) {
				String exception = "";
				if (record.getThrown() != null) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					pw.println();
					record.getThrown().printStackTrace(pw);
					pw.close();
					exception = sw.toString();
				}

				return MessageFormat.format("[{0}] [{1}] {2}{3}\n",
						convertLoggerName(record.getLoggerName()),
						record.getLevel().getName(),
						record.getMessage(),
						exception);
			}
		}) {
			@Override
			public synchronized void publish(LogRecord record) {
				super.publish(record);
				flush();
			}
		};
		handler.setLevel(Level.ALL);
		handler.setFilter(createFilter());
		ROOT.addHandler(handler);
	}

	private static Predicate<String> createDebugLoggerNamePredicate() {
		String argument = System.getProperty(PROP_DEBUG);
		if (argument == null) {
			return any -> false;
		} else {
			Set<String> debugLoggers = new HashSet<>();
			for (String element : argument.split(",")) {
				if (element.equals("true") || element.equals("all")) {
					return loggerName -> loggerName.startsWith(PREFIX);
				} else {
					debugLoggers.add(PREFIX + "." + element);
				}
			}
			return debugLoggers::contains;
		}
	}

	private static Filter createFilter() {
		return log -> log.getLevel().intValue() >= Level.INFO.intValue() || isDebugOnFor(log.getLoggerName());
	}

	public static boolean isDebugOnFor(String loggerName) {
		return debugLoggerNamePredicate.test(loggerName);
	}
}
