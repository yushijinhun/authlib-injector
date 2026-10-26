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
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.UUID;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.util.CertificateSignatureVerifier;
import moe.yushi.authlibinjector.util.KeyUtils;

/**
 * Lets BungeeCord validate profile keys against the configured certificate trust set.
 * See https://github.com/SpigotMC/BungeeCord/commit/78ca16dfe3bf9a21d5c054a1884d4f5f198a62bc .
 */
public class BungeeCordProfileKeyTransformUnit implements TransformUnit {

	@CallbackMethod
	public static boolean verifyCertificate(Object key, UUID holder) {
		try {
			Class<?> type = key.getClass();
			return CertificateSignatureVerifier.verify(
					KeyUtils.parseX509PublicKey((byte[]) type.getMethod("getKey").invoke(key)),
					((Number) type.getMethod("getExpiry").invoke(key)).longValue(), holder,
					(byte[]) type.getMethod("getSignature").invoke(key));
		} catch (ReflectiveOperationException | GeneralSecurityException | RuntimeException e) {
			return false;
		}
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("net.md_5.bungee.EncryptionUtil".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("check".equals(name) && ("(Lnet/md_5/bungee/protocol/PlayerPublicKey;Ljava/util/UUID;)Z".equals(descriptor)
							|| "(Lnet/md_5/bungee/protocol/data/PlayerPublicKey;Ljava/util/UUID;)Z".equals(descriptor))) {
						ctx.markModified();

						MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitVarInsn(ALOAD, 1);
						ctx.invokeCallback(mv, BungeeCordProfileKeyTransformUnit.class, "verifyCertificate");
						mv.visitInsn(IRETURN);
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
		return "BungeeCord Profile Key Transformer";
	}
}
