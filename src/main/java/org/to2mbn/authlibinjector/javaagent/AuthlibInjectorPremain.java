package org.to2mbn.authlibinjector.javaagent;

import static org.to2mbn.authlibinjector.AuthlibInjector.bootstrap;
import static org.to2mbn.authlibinjector.AuthlibInjector.info;
import java.lang.instrument.Instrumentation;

public class AuthlibInjectorPremain {

	public static void premain(String arg, Instrumentation instrumentation) {
		try {
			info("launched from javaagent");
			if (arg != null && !arg.isEmpty()) {
				System.setProperty("org.to2mbn.authlibinjector.config", arg);
			}
			bootstrap(instrumentation::addTransformer);
		} catch (Throwable e) {
			// prevent the exception being thrown to VM
			e.printStackTrace();
		}
	}
}
