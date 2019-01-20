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
package moe.yushi.authlibinjector.transform.support;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;

import java.util.Optional;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import moe.yushi.authlibinjector.transform.TransformUnit;

public class LaunchWrapperTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("net.minecraft.launchwrapper.LaunchClassLoader".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("<init>".equals(name)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitInsn(int opcode) {
								if (opcode == RETURN) {
									modifiedCallback.run();
									super.visitVarInsn(ALOAD, 0);
									super.visitLdcInsn("moe.yushi.authlibinjector.");
									super.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/launchwrapper/LaunchClassLoader", "addClassLoaderExclusion", "(Ljava/lang/String;)V", false);
								}
								super.visitInsn(opcode);
							}
						};
					} else {
						return super.visitMethod(access, name, descriptor, signature, exceptions);
					}
				}
			});
		} else {
			return Optional.empty();
		}
	}

	@Override
	public String toString() {
		return "LaunchWrapper Support";
	}
}
