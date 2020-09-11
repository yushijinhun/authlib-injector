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
package moe.yushi.authlibinjector.transform.support;

import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProxyParameterWorkaround {
	private ProxyParameterWorkaround() {}

	private static final Set<String> PROXY_PARAMETERS = new HashSet<>(Arrays.asList(
			"--proxyHost", "--proxyPort", "--proxyUser", "--proxyPass"
	));

	public static void init() {
		MainArgumentsTransformer.getArgumentsListeners().add(args -> {
			boolean proxyDetected = false;
			List<String> filtered = new ArrayList<>();
			for (int i = 0; i < args.length; i++) {
				if (i + 1 < args.length && PROXY_PARAMETERS.contains(args[i])) {
					proxyDetected = true;
					log(WARNING, "Dropping main argument " + args[i] + " " + args[i + 1]);
					i++;
					continue;
				}
				filtered.add(args[i]);
			}
			if (proxyDetected) {
				log(WARNING, "--proxyHost parameter conflicts with authlib-injector, therefore proxy is disabled.");
			}
			return filtered.toArray(new String[filtered.size()]);
		});
	}
}
