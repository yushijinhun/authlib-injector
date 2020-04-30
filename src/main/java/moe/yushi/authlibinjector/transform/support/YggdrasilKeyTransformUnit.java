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

import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import java.lang.invoke.MethodHandle;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

public class YggdrasilKeyTransformUnit implements TransformUnit {

	public static final List<PublicKey> PUBLIC_KEYS = new CopyOnWriteArrayList<>();

	@CallbackMethod
	public static boolean verifyPropertySignature(Object property, PublicKey mojangKey, MethodHandle verifyAction) throws Throwable {
		if ((boolean) verifyAction.invoke(property, mojangKey)) {
			return true;
		}
		for (PublicKey customKey : PUBLIC_KEYS) {
			if ((boolean) verifyAction.invoke(property, customKey)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					return new MethodVisitor(ASM7, super.visitMethod(access, name, desc, signature, exceptions)) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
							if (opcode == INVOKEVIRTUAL
									&& "com/mojang/authlib/properties/Property".equals(owner)
									&& "isSignatureValid".equals(name)
									&& "(Ljava/security/PublicKey;)Z".equals(descriptor)) {
								ctx.markModified();
								super.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, owner, name, descriptor, isInterface));
								CallbackSupport.invoke(ctx, this, YggdrasilKeyTransformUnit.class, "verifyPropertySignature");
							} else {
								super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
							}
						}
					};
				}

			});
		} else {
			return Optional.empty();
		}
	}

	@Override
	public String toString() {
		return "Yggdrasil Public Key Transformer";
	}
}
