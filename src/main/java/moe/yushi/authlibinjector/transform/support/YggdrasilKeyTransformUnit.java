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
import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.IRETURN;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.KeyUtils;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.Logging.Level;

public class YggdrasilKeyTransformUnit implements TransformUnit {

	public static final List<PublicKey> PUBLIC_KEYS = new CopyOnWriteArrayList<>();

	static {
		PUBLIC_KEYS.add(loadMojangPublicKey());
	}

	private static PublicKey loadMojangPublicKey() {
		try (InputStream in = YggdrasilKeyTransformUnit.class.getResourceAsStream("/mojang_publickey.der")) {
			return KeyUtils.parseX509PublicKey(asBytes(in));
		} catch (GeneralSecurityException | IOException e) {
			throw new RuntimeException("Failed to load Mojang public key", e);
		}
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
		try {
			sig = Base64.getDecoder().decode(base64Signature);
		} catch (IllegalArgumentException e) {
			Logging.log(Level.ERROR, "Malformed signature encoding on property " + propertyObj.toString(), e);
			return false;
		}
		byte[] data = propertyValue.getBytes();

		for (PublicKey customKey : PUBLIC_KEYS) {
			try {
				Signature signature = Signature.getInstance("SHA1withRSA");
				signature.initVerify(customKey);
				signature.update(data);
				if (signature.verify(sig))
					return true;
			} catch (GeneralSecurityException e) {
				Logging.log(DEBUG, "Failed to verify signature with key " + customKey, e);
			}
		}

		Logging.log(Level.WARNING, "Failed to verify property signature");
		return false;
	}

	@CallbackMethod
	public static Signature createDummySignature() {
		Signature sig = new Signature("authlib-injector-dummy-verify") {

			@Override
			protected boolean engineVerify(byte[] sigBytes) {
				return true;
			}

			@Override
			protected void engineUpdate(byte[] b, int off, int len) {

			}

			@Override
			protected void engineUpdate(byte b) {
			}

			@Override
			protected byte[] engineSign() {
				throw new UnsupportedOperationException();
			}

			@Override
			@Deprecated
			protected void engineSetParameter(String param, Object value) {

			}

			@Override
			protected void engineInitVerify(PublicKey publicKey) {
			}

			@Override
			protected void engineInitSign(PrivateKey privateKey) {
				throw new UnsupportedOperationException();
			}

			@Override
			@Deprecated
			protected Object engineGetParameter(String param) {
				return null;
			}
		};
		try {
			sig.initVerify((PublicKey) null);
		} catch (InvalidKeyException e) {
			throw new RuntimeException(e);
		}
		return sig;
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

		} else if ("com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo".equals(className)) {
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
						ctx.invokeCallback(mv, YggdrasilKeyTransformUnit.class, "createDummySignature");
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
