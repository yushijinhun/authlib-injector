/*
 * Copyright (C) 2020  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector;

import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static moe.yushi.authlibinjector.util.Logging.Level.ERROR;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import java.lang.instrument.Instrumentation;

public final class Premain {
	private Premain() {}

	public static void premain(String arg, Instrumentation instrumentation) {
		try {
			initInjector(arg, instrumentation, false);
		} catch (InitializationException e) {
			log(DEBUG, "A known exception has occurred", e);
			System.exit(1);
		} catch (Throwable e) {
			log(ERROR, "An exception has occurred, exiting", e);
			System.exit(1);
		}
	}

	public static void agentmain(String arg, Instrumentation instrumentation) {
		try {
			log(INFO, "Launched from agentmain");
			initInjector(arg, instrumentation, true);
		} catch (InitializationException e) {
			log(DEBUG, "A known exception has occurred", e);
		} catch (Throwable e) {
			log(ERROR, "An exception has occurred", e);
		}
	}

	private static void initInjector(String arg, Instrumentation instrumentation, boolean retransform) {
		AuthlibInjector.bootstrap(instrumentation, arg);

		if (retransform) {
			AuthlibInjector.retransformAllClasses();
		}
	}
}
