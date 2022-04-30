/*
 * Copyright (C) 2022  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import static org.objectweb.asm.Opcodes.*;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

/**
 * Starting from 22w06a, Minecraft allows only certain ASCII characters (33 ~ 126)
 * in the username. This transformer removes the restriction.
 */
public class UsernameCharacterCheckTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext context) {
		if (!context.getStringConstants().contains("Invalid characters in username")) {
			return Optional.empty();
		}

		return Optional.of(new ClassVisitor(ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {

					// States:
					// 0 - initial state
					// 1 - ldc_w "Invalid characters in username"
					// 2 - iconst_0
					// 3 - anewarray java/lang/Object
					// 4 - invokestatic org/apache/commons/lang3/Validate.validState:(ZLjava/lang/String;[Ljava/lang/Object;)V
					int state = 0;

					@Override
					public void visitLdcInsn(Object value) {
						if (state == 0 && "Invalid characters in username".equals(value)) {
							state++;
						}
						super.visitLdcInsn(value);
					}

					@Override
					public void visitInsn(int opcode) {
						if (state == 1 && opcode == ICONST_0) {
							state++;
						}
						super.visitInsn(opcode);
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if (state == 2 && opcode == ANEWARRAY && "java/lang/Object".equals(type)) {
							state++;
						}
						super.visitTypeInsn(opcode, type);
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
						if (state == 3 &&
								opcode == INVOKESTATIC &&
								"org/apache/commons/lang3/Validate".equals(owner) &&
								"validState".equals(name) &&
								"(ZLjava/lang/String;[Ljava/lang/Object;)V".equals(descriptor)) {
							context.markModified();
							state++;

							super.visitInsn(POP);
							super.visitInsn(POP);
							super.visitInsn(POP);

						} else {
							super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
						}
					}
				};
			}
		});
	}

	@Override
	public String toString() {
		return "Username Character Checker Transformer";
	}
}
