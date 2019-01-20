/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package moe.yushi.authlibinjector.javaagent;

import java.lang.instrument.Instrumentation;
import java.util.logging.Level;

import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.InjectorInitializationException;
import moe.yushi.authlibinjector.util.Logging;

public class AuthlibInjectorPremain {

	public static void premain(String arg, Instrumentation instrumentation) {
		try {
			initInjector(arg, instrumentation, false);
		} catch (InjectorInitializationException e) {
			Logging.LAUNCH.log(Level.FINE, "A known exception has occurred", e);
			System.exit(1);
		} catch (Throwable e) {
			Logging.LAUNCH.log(Level.SEVERE, "An exception has occurred, exiting", e);
			System.exit(1);
		}
	}

	public static void agentmain(String arg, Instrumentation instrumentation) {
		try {
			Logging.LAUNCH.info("Launched from agentmain");
			initInjector(arg, instrumentation, true);
		} catch (InjectorInitializationException e) {
			Logging.LAUNCH.log(Level.FINE, "A known exception has occurred", e);
		} catch (Throwable e) {
			Logging.LAUNCH.log(Level.SEVERE, "An exception has occurred", e);
		}
	}

	public static void initInjector(String arg, Instrumentation instrumentation, boolean retransform) {
		setupConfig(arg);
		AuthlibInjector.bootstrap(instrumentation);

		if (retransform) {
			AuthlibInjector.retransformAllClasses();
		}
	}

	private static void setupConfig(String arg) {
		if (arg != null && !arg.isEmpty()) {
			System.setProperty(AuthlibInjector.PROP_API_ROOT, arg);
		}
	}
}
