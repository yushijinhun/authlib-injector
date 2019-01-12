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
package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ASM7;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public abstract class LdcTransformUnit implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		return Optional.of(new ClassVisitor(ASM7, writer) {

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				return new MethodVisitor(ASM7, super.visitMethod(access, name, desc, signature, exceptions)) {

					@Override
					public void visitLdcInsn(Object cst) {
						if (cst instanceof String) {
							Optional<String> transformed = transformLdc((String) cst);
							if (transformed.isPresent() && !transformed.get().equals(cst)) {
								modifiedCallback.run();
								super.visitLdcInsn(transformed.get());
							} else {
								super.visitLdcInsn(cst);
							}
						} else {
							super.visitLdcInsn(cst);
						}
					}
				};
			}
		});
	}

	protected abstract Optional<String> transformLdc(String input);
}
