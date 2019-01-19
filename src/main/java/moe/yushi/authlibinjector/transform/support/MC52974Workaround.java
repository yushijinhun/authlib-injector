package moe.yushi.authlibinjector.transform.support;

import static java.util.Collections.unmodifiableSet;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ISTORE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

/**
 * See <https://github.com/yushijinhun/authlib-injector/issues/30>
 */
public class MC52974Workaround implements TransformUnit {

	private static boolean affected = false;

	// ==== Detect affected versions ====
	public static final Set<String> AFFECTED_VERSION_SERIES = unmodifiableSet(new HashSet<>(Arrays.asList(
			"1.7.4", // MC 1.7.9 uses this
			"1.7.10",
			"1.8",
			"1.9",
			"1.10",
			"1.11",
			"1.12")));

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

	public static void acceptMainArguments(String[] args) {
		inferVersionSeries(args).ifPresent(assetIndexName -> {
			if (AFFECTED_VERSION_SERIES.contains(assetIndexName)) {
				Logging.HTTPD.info("Current version series is " + assetIndexName + ", enable MC-52974 workaround.");
				affected = true;
			}
		});
	}
	// ====

	public static boolean overwriteRequireSecure(boolean requireSecure) {
		if (affected) {
			return true;
		}
		return requireSecure;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("fillGameProfile".equals(name) && "(Lcom/mojang/authlib/GameProfile;Z)Lcom/mojang/authlib/GameProfile;".equals(descriptor)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitCode() {
								super.visitCode();
								modifiedCallback.run();
								super.visitVarInsn(ILOAD, 2);
								super.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MC52974Workaround.class), "overwriteRequireSecure", "(Z)Z", false);
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
