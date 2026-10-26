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

import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.IRETURN;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.util.CertificateSignatureVerifier;

/**
 * Lets Velocity validate profile keys against the configured certificate trust set.
 * See https://github.com/PaperMC/Velocity/commit/1a3fba4250553702d9dcd05731d04347bfc24c9f .
 */
public class VelocityProfileKeyTransformUnit implements TransformUnit {

	@CallbackMethod
	public static boolean verifyCurrentCertificate(Object key) {
		try {
			UUID holder = (UUID) key.getClass().getMethod("getSignatureHolder").invoke(key);
			return Boolean.TRUE.equals(verifyCertificate(key, holder));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	@CallbackMethod
	public static Boolean verifyCertificate(Object key, UUID holder) {
		try {
			Class<?> type = key.getClass();
			String revision = ((Enum<?>) type.getMethod("getKeyRevision").invoke(key)).name();
			if ("LINKED_V2".equals(revision)) {
				if (holder == null) {
					return null;
				}
			} else if ("GENERIC_V1".equals(revision)) {
				holder = null;
			} else {
				return false;
			}
			return CertificateSignatureVerifier.verify(
					(PublicKey) type.getMethod("getSignedPublicKey").invoke(key),
					((Instant) type.getMethod("getExpiryTemporal").invoke(key)).toEpochMilli(), holder,
					(byte[]) type.getMethod("getSignature").invoke(key));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.velocitypowered.proxy.crypto.IdentifiedKeyImpl".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("isSignatureValid".equals(name) && "()Z".equals(descriptor)) {
						// Native Velocity caches Boolean results on the key object. Recheck
						// the current trust set so both revocation and newly approved keys work.
						ctx.markModified();
						MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						ctx.invokeCallback(mv, VelocityProfileKeyTransformUnit.class, "verifyCurrentCertificate");
						mv.visitInsn(IRETURN);
						mv.visitMaxs(-1, -1);
						mv.visitEnd();
						return null;
					} else if ("validateData".equals(name) && "(Ljava/util/UUID;)Ljava/lang/Boolean;".equals(descriptor)) {
						ctx.markModified();

						MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitVarInsn(ALOAD, 1);
						ctx.invokeCallback(mv, VelocityProfileKeyTransformUnit.class, "verifyCertificate");
						mv.visitInsn(ARETURN);
						mv.visitMaxs(-1, -1);
						mv.visitEnd();

						return null;
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
		return "Velocity Profile Key Transformer";
	}
}
