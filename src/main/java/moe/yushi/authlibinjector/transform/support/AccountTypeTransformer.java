/*
 * Copyright (C) 2023  Haowei Wen <yushijinhun@gmail.com> and contributors
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
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;

public class AccountTypeTransformer {

	public String[] transform(String[] args) {
		boolean userTypeMatched = false;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--userType".equals(arg)) {
				userTypeMatched = true;
			} else if (userTypeMatched && "mojang".equals(arg)) {
				args[i] = "msa";
				log(INFO, "Setting accountType to msa");
				break;
			}
		}
		return args;
	}

}
