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

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformUnit;

public class LaunchwrapperExclusionTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext context) {
		if ("net.minecraft.launchwrapper.LaunchClassLoader".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("<init>".equals(name)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {

							boolean exclusionAdded = false;

							@Override
							public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
								super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

								if (!exclusionAdded &&
										opcode == INVOKEVIRTUAL &&
										"net/minecraft/launchwrapper/LaunchClassLoader".equals(owner) &&
										"addClassLoaderExclusion".equals(name) &&
										"(Ljava/lang/String;)V".equals(descriptor)) {
									super.visitVarInsn(ALOAD, 0);
									super.visitLdcInsn("moe.yushi.authlibinjector.");
									super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
									exclusionAdded = true;
									context.markModified();
								}
							}
						};
					}
					return super.visitMethod(access, name, descriptor, signature, exceptions);
				}
			});
		} else {
			return Optional.empty();
		}
	}

	@Override
	public String toString() {
		return "Launchwrapper ClassLoader Exclusion";
	}
}
