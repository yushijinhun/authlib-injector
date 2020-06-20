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

import static java.util.Collections.unmodifiableSet;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ISTORE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

/**
 * See <https://github.com/yushijinhun/authlib-injector/issues/30>
 */
public class MC52974Workaround implements TransformUnit {

	private MC52974Workaround() {
	}

	private static final Set<String> AFFECTED_VERSION_SERIES = unmodifiableSet(new HashSet<>(Arrays.asList(
			"1.7.10",
			"1.8",
			"1.9",
			"1.10",
			"1.11",
			"1.12")));

	public static void init() {
		MainArgumentsTransformer.getVersionSeriesListeners().add(version -> {
			if (AFFECTED_VERSION_SERIES.contains(version)) {
				Logging.TRANSFORM.info("Enable MC-52974 Workaround");
				AuthlibInjector.getClassTransformer().units.add(new MC52974Workaround());
				AuthlibInjector.retransformClasses("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService");
			}
		});
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("fillGameProfile".equals(name) && "(Lcom/mojang/authlib/GameProfile;Z)Lcom/mojang/authlib/GameProfile;".equals(descriptor)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitCode() {
								super.visitCode();
								ctx.markModified();
								super.visitLdcInsn(1);
								super.visitVarInsn(ISTORE, 2);
							}
						};
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
		return "MC-52974 Workaround";
	}
}
