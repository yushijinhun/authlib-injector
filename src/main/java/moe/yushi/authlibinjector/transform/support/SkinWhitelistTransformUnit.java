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

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.SIPUSH;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import moe.yushi.authlibinjector.transform.TransformUnit;

public class SkinWhitelistTransformUnit implements TransformUnit {

	private String[] skinWhitelist;

	public SkinWhitelistTransformUnit(String[] skinWhitelist) {
		this.skinWhitelist = skinWhitelist;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {

				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					if ("<clinit>".equals(name)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, desc, signature, exceptions)) {

							int status = 0;

							@Override
							public void visitInsn(int opcode) {
								if (status == 0 && opcode == ICONST_2) {
									status++;
								} else if ((status == 2 || status == 6) && opcode == DUP) {
									status++;
								} else if (status == 3 && opcode == ICONST_0) {
									status++;
								} else if ((status == 5 || status == 9) && opcode == AASTORE) {
									status++;
									if (status == 10) {
										ctx.markModified();
										super.visitIntInsn(SIPUSH, skinWhitelist.length + 2);
										super.visitTypeInsn(ANEWARRAY, "java/lang/String");
										super.visitInsn(DUP);
										super.visitInsn(ICONST_0);
										super.visitLdcInsn(".minecraft.net");
										super.visitInsn(AASTORE);
										super.visitInsn(DUP);
										super.visitInsn(ICONST_1);
										super.visitLdcInsn(".mojang.com");
										super.visitInsn(AASTORE);
										for (int i = 0; i < skinWhitelist.length; i++) {
											super.visitInsn(DUP);
											super.visitIntInsn(SIPUSH, i + 2);
											super.visitLdcInsn(skinWhitelist[i]);
											super.visitInsn(AASTORE);
										}
									}
								} else if (status == 7 && opcode == ICONST_1) {
									status++;
								} else {
									super.visitInsn(opcode);
								}
							}

							@Override
							public void visitTypeInsn(int opcode, String type) {
								if (status == 1 && opcode == ANEWARRAY && "java/lang/String".equals(type)) {
									status++;
								} else {
									super.visitTypeInsn(opcode, type);
								}
							}

							@Override
							public void visitLdcInsn(Object cst) {
								if (status == 4 && ".minecraft.net".equals(cst)) {
									status++;
								} else if (status == 8 && ".mojang.com".equals(cst)) {
									status++;
								} else {
									super.visitLdcInsn(cst);
								}
							}

						};
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
		return "Texture Whitelist Transformer";
	}
}
