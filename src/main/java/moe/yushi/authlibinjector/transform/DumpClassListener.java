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
package moe.yushi.authlibinjector.transform;

import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class DumpClassListener implements ClassLoadingListener {

	private Path outputPath;

	public DumpClassListener(Path outputPath) {
		this.outputPath = outputPath;
	}

	@Override
	public void onClassLoading(ClassLoader classLoader, String className, byte[] bytecode, List<TransformUnit> appliedTransformers) {
		if (!appliedTransformers.isEmpty()) {
			Path dumpFile = outputPath.resolve(className + "_dump.class");
			try {
				Files.write(dumpFile, bytecode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				log(INFO,"Transformed class is dumped to [" + dumpFile + "]");
			} catch (IOException e) {
				log(WARNING, "Failed to dump class [" + className + "]", e);
			}
		}
	}
}
