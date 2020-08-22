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
package moe.yushi.authlibinjector.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import moe.yushi.authlibinjector.Config;

public final class Logging {
	private Logging() {}

	private static final PrintStream out = System.err;

	public static enum Level {
		DEBUG, INFO, WARNING, ERROR;
	}

	public static void log(Level level, String message) {
		log(level, message, null);
	}

	public static void log(Level level, String message, Throwable e) {
		if (level == Level.DEBUG && !Config.verboseLogging) {
			return;
		}
		String log = "[authlib-injector] [" + level + "] " + message;
		if (e != null) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			e.printStackTrace(pw);
			pw.close();
			log += sw.toString();
		}
		// remove control characters to prevent messing up the console
		log = log.replaceAll("[\\p{Cc}&&[^\r\n\t]]", "");
		out.println(log);
	}
}
