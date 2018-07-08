package moe.yushi.authlibinjector.util;

import static moe.yushi.authlibinjector.AuthlibInjector.PROP_DEBUG;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

public final class Logging {
	private Logging() {}

	public static final Logger ROOT = Logger.getLogger("moe.yushi.authlibinjector");
	public static final Logger LAUNCH = Logger.getLogger("moe.yushi.authlibinjector.launch");
	public static final Logger CONFIG = Logger.getLogger("moe.yushi.authlibinjector.config");
	public static final Logger TRANSFORM = Logger.getLogger("moe.yushi.authlibinjector.transform");
	public static final Logger HTTPD = Logger.getLogger("moe.yushi.authlibinjector.httpd");

	public static void init() {
		initRootLogger();
		initLoggingLevels();
	}

	private static void initRootLogger() {
		ROOT.setLevel(Level.INFO);
		ROOT.setUseParentHandlers(false);
		StreamHandler handler = new StreamHandler(System.err, new Formatter() {
			private String convertLoggerName(String loggerName) {
				final String prefix = "moe.yushi.authlibinjector";
				if (loggerName.startsWith(prefix)) {
					return "authlib-injector" + loggerName.substring(prefix.length());
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
		handler.setFilter(null);
		ROOT.addHandler(handler);
	}

	private static void initLoggingLevels() {
		if ("true".equals(System.getProperty(PROP_DEBUG))) {
			ROOT.setLevel(Level.ALL);
		}
	}

}
