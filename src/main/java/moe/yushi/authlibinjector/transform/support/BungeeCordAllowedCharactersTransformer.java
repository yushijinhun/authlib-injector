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

import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.ISTORE;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

/**
 * Hacks BungeeCord to allow special characters to occur in the username.
 *
 * Since <https://github.com/SpigotMC/BungeeCord/commit/3008d7ef2f50de7e3d38e76717df72dac7fe0da3>,
 * BungeeCord allows only certain characters to occur in the username when online-mode is on.
 */
public class BungeeCordAllowedCharactersTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext context) {
		if ("net.md_5.bungee.util.AllowedCharacters".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("isValidName".equals(name) && "(Ljava/lang/String;Z)Z".equals(descriptor)) {
						return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitCode() {
								super.visitCode();
								super.visitLdcInsn(0);
								super.visitVarInsn(ISTORE, 1);
								context.markModified();
							}
						};
					}
					return super.visitMethod(access, name, descriptor, signature, exceptions);
				}
			});
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "BungeeCord Allowed Characters Transformer";
	}
}
