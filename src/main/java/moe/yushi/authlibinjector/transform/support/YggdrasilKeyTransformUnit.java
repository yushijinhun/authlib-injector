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

import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import java.lang.invoke.MethodHandle;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
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
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.Logging.Level;

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

	@CallbackMethod
	public static boolean verifyPropertySignatureNew(Signature mojangSignatureObj, String propertyValue, String base64Signature) {
		byte[] sig = Base64.getDecoder().decode(base64Signature);
		byte[] data = propertyValue.getBytes();

		try {
			mojangSignatureObj.update(data);
			if (mojangSignatureObj.verify(sig))
				return true;
		} catch (SignatureException e) {
			Logging.log(DEBUG, "Failed to verify signature with Mojang's key", e);
		}

		for (PublicKey customKey : PUBLIC_KEYS) {
			try {
				Signature signature = Signature.getInstance("SHA1withRSA");
				signature.initVerify(customKey);
				signature.update(data);
				if (signature.verify(sig))
					return true;
			} catch (GeneralSecurityException e) {
				Logging.log(DEBUG, "Failed to verify signature with custom key " + customKey, e);
			}
		}

		Logging.log(Level.WARNING, "Failed to verify property signature");
		return false;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					return new MethodVisitor(ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
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
		} else if ("com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					if ("validateProperty".equals(name) && "(Lcom/mojang/authlib/properties/Property;)Z".equals(desc)) {
						ctx.markModified();

						MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitMethodInsn(INVOKEVIRTUAL, "com/mojang/authlib/yggdrasil/YggdrasilServicesKeyInfo", "signature", "()Ljava/security/Signature;", false);
						mv.visitVarInsn(ALOAD, 1);
						mv.visitMethodInsn(INVOKEVIRTUAL, "com/mojang/authlib/properties/Property", "getValue", "()Ljava/lang/String;", false);
						mv.visitVarInsn(ALOAD, 1);
						mv.visitMethodInsn(INVOKEVIRTUAL, "com/mojang/authlib/properties/Property", "getSignature", "()Ljava/lang/String;", false);
						CallbackSupport.invoke(ctx, mv, YggdrasilKeyTransformUnit.class, "verifyPropertySignatureNew");
						mv.visitInsn(IRETURN);
						mv.visitMaxs(-1, -1);
						mv.visitEnd();

						return null;
					} else {
						return super.visitMethod(access, name, desc, signature, exceptions);
					}
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
