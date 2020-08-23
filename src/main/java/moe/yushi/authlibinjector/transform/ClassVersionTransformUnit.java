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
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static org.objectweb.asm.Opcodes.ASM7;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;

class ClassVersionTransformUnit implements TransformUnit {

	private final int minVersion;
	private final int upgradedVersion;

	public ClassVersionTransformUnit(int minVersion, int upgradedVersion) {
		this.minVersion = minVersion;
		this.upgradedVersion = upgradedVersion;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext context) {
		return Optional.of(new ClassVisitor(ASM7, writer) {
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				int major = version & 0xffff;

				if (minVersion != -1 && major < minVersion) {
					throw new ClassVersionException("class version (" + major + ") is lower than required(" + minVersion + ")");
				}

				if (upgradedVersion != -1 && major < upgradedVersion) {
					log(DEBUG,"Upgrading class version from " + major + " to " + upgradedVersion);
					version = upgradedVersion;
					context.markModified();
				}
				super.visit(version, access, name, signature, superName, interfaces);
			}
		});
	}

	@Override
	public String toString() {
		return "Class File Version Transformer";
	}
}
