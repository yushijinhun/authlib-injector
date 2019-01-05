package moe.yushi.authlibinjector.transform;

import static java.util.stream.Collectors.joining;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import moe.yushi.authlibinjector.util.Logging;

public class MainArgumentsTransformer implements TransformUnit {

	private static final List<Function<String[], String[]>> LISTENERS = new CopyOnWriteArrayList<>();

	public static String[] processMainArguments(String[] args) {
		Logging.TRANSFORM.fine(() -> "Main arguments: " + Stream.of(args).collect(joining(" ")));

		String[] result = args;
		for (Function<String[], String[]> listener : LISTENERS) {
			result = listener.apply(result);
		}
		return result;
	}

	public static List<Function<String[], String[]>> getListeners() {
		return LISTENERS;
	}

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
}
