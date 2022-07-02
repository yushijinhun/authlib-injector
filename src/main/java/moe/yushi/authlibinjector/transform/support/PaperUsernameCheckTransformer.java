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
 * Disables PaperMC's username check.
 * See <https://github.com/PaperMC/Paper/blob/master/patches/server/0823-Validate-usernames.patch>.
 */
public class PaperUsernameCheckTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext context) {
		if (!context.getStringConstants().contains("Invalid characters in username")) {
			return Optional.empty();
		}

		return Optional.of(new ClassVisitor(ASM9, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {

					@Override
					public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
						if (opcode == GETFIELD && "iKnowThisMayNotBeTheBestIdeaButPleaseDisableUsernameValidation".equals(name)) {
							context.markModified();
							visitInsn(POP);
							visitInsn(ICONST_1);
						} else {
							super.visitFieldInsn(opcode, owner, name, descriptor);
						}
					}
				};
			}
		});
	}

	@Override
	public String toString() {
		return "Paper Username Checker Transformer";
	}
}
