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
package moe.yushi.authlibinjector.transform;

import static java.util.stream.Collectors.joining;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import moe.yushi.authlibinjector.util.Logging;

public class MainArgumentsTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("net.minecraft.client.main.Main".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("main".equals(name) && "([Ljava/lang/String;)V".equals(descriptor)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitCode() {
								super.visitCode();
								modifiedCallback.run();

								super.visitVarInsn(ALOAD, 0);
								super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MainArgumentsTransformer.class), "processMainArguments", "([Ljava/lang/String;)[Ljava/lang/String;", false);
								super.visitVarInsn(ASTORE, 0);
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
		return "Main Arguments Transformer";
	}

	// ==== Main arguments processing ====
	private static final List<Function<String[], String[]>> ARGUMENTS_LISTENERS = new CopyOnWriteArrayList<>();

	public static String[] processMainArguments(String[] args) {
		Logging.TRANSFORM.fine(() -> "Main arguments: " + Stream.of(args).collect(joining(" ")));

		String[] result = args;
		for (Function<String[], String[]> listener : ARGUMENTS_LISTENERS) {
			result = listener.apply(result);
		}
		return result;
	}

	public static List<Function<String[], String[]>> getArgumentsListeners() {
		return ARGUMENTS_LISTENERS;
	}
	// ====

	// ==== Version series detection ====
	private static final List<Consumer<String>> VERSION_SERIES_LISTENERS = new CopyOnWriteArrayList<>();

	public static Optional<String> inferVersionSeries(String[] args) {
		boolean hit = false;
		for (String arg : args) {
			if (hit) {
				if (arg.startsWith("--")) {
					// arg doesn't seem to be a value
					// maybe the previous argument is a value, but we wrongly recognized it as an option
					hit = false;
				} else {
					return Optional.of(arg);
				}
			}

			if ("--assetIndex".equals(arg)) {
				hit = true;
			}
		}
		return Optional.empty();
	}

	static {
		getArgumentsListeners().add(args -> {
			inferVersionSeries(args).ifPresent(versionSeries -> {
				Logging.TRANSFORM.fine("Version series detected: " + versionSeries);
				VERSION_SERIES_LISTENERS.forEach(listener -> listener.accept(versionSeries));
			});
			return args;
		});
	}

	public static List<Consumer<String>> getVersionSeriesListeners() {
		return VERSION_SERIES_LISTENERS;
	}
	// ====
}
