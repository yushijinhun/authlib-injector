/*
 * Copyright (C) 2023  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.IRETURN;
import java.lang.invoke.MethodHandle;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.Logging.Level;
import moe.yushi.authlibinjector.util.MultiKeySignature;
import moe.yushi.authlibinjector.yggdrasil.PublicKeyManager;
import moe.yushi.authlibinjector.yggdrasil.PublicKeyManager.Purpose;
import moe.yushi.authlibinjector.yggdrasil.PublicKeys;

public class YggdrasilKeyTransformUnit implements TransformUnit {

	private static volatile PublicKeyManager keyManager = PublicKeyManager.fixed(PublicKeys.mojang());

	public static PublicKeys getPublicKeys() {
		return keyManager.currentKeys();
	}

	public static void setPublicKeys(PublicKeys keys) {
		setPublicKeyManager(PublicKeyManager.fixed(keys));
	}

	public static PublicKeyManager getPublicKeyManager() {
		return keyManager;
	}

	public static synchronized void setPublicKeyManager(PublicKeyManager manager) {
		PublicKeyManager previous = keyManager;
		keyManager = requireNonNull(manager);
		if (previous != manager) previous.close();
	}

	@CallbackMethod
	public static boolean verifyPropertySignature(Object propertyObj) {
		String base64Signature;
		String propertyValue;

		try {
			MethodHandle valueHandle;
			try {
				valueHandle = publicLookup().findVirtual(propertyObj.getClass(), "getValue", methodType(String.class));
			} catch (NoSuchMethodException ignored) {
				valueHandle = publicLookup().findVirtual(propertyObj.getClass(), "value", methodType(String.class));
			}

			MethodHandle signatureHandle;
			try {
				signatureHandle = publicLookup().findVirtual(propertyObj.getClass(), "getSignature", methodType(String.class));
			} catch(NoSuchMethodException ignored) {
				signatureHandle = publicLookup().findVirtual(propertyObj.getClass(), "signature", methodType(String.class));
			}

			base64Signature = (String) signatureHandle.invokeWithArguments(propertyObj);
			propertyValue = (String) valueHandle.invokeWithArguments(propertyObj);
		} catch (Throwable e) {
			Logging.log(Level.ERROR, "Failed to get property attributes", e);
			return false;
		}

		byte[] sig;
		if (base64Signature == null || propertyValue == null) {
			return false;
		}
		try {
			sig = Base64.getDecoder().decode(base64Signature);
		} catch (IllegalArgumentException e) {
			Logging.log(Level.ERROR, "Malformed signature encoding on property " + propertyObj.toString(), e);
			return false;
		}
		byte[] data = propertyValue.getBytes(UTF_8);

		return keyManager.verify(Purpose.PROFILE_PROPERTY, data, sig);
	}

	@CallbackMethod
	public static Signature createCertificateSignature() {
		return new MultiKeySignature((data, signature) -> keyManager.verify(Purpose.PLAYER_CERTIFICATE, data, signature));
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.mojang.authlib.properties.Property".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					if ("isSignatureValid".equals(name) && "(Ljava/security/PublicKey;)Z".equals(desc)) {
						ctx.markModified();

						MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						ctx.invokeCallback(mv, YggdrasilKeyTransformUnit.class, "verifyPropertySignature");
						mv.visitInsn(IRETURN);
						mv.visitMaxs(-1, -1);
						mv.visitEnd();

						return null;
					} else {
						return super.visitMethod(access, name, desc, signature, exceptions);
					}
				}
			});

		} else if ("com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo".equals(className) || "com.mojang.authlib.services.MinecraftServicesKeyInfo".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					if ("validateProperty".equals(name) && "(Lcom/mojang/authlib/properties/Property;)Z".equals(desc)) {
						ctx.markModified();

						MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 1);
						ctx.invokeCallback(mv, YggdrasilKeyTransformUnit.class, "verifyPropertySignature");
						mv.visitInsn(IRETURN);
						mv.visitMaxs(-1, -1);
						mv.visitEnd();

						return null;

					} else if ("signature".equals(name) && "()Ljava/security/Signature;".equals(desc)) {
						ctx.markModified();

						MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
						mv.visitCode();
						ctx.invokeCallback(mv, YggdrasilKeyTransformUnit.class, "createCertificateSignature");
						mv.visitInsn(ARETURN);
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
