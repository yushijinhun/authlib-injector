package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ASM7;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public abstract class LdcTransformUnit implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		return Optional.of(new ClassVisitor(ASM7, writer) {

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				return new MethodVisitor(ASM7, super.visitMethod(access, name, desc, signature, exceptions)) {

					@Override
					public void visitLdcInsn(Object cst) {
						if (cst instanceof String) {
							Optional<String> transformed = transformLdc((String) cst);
							if (transformed.isPresent() && !transformed.get().equals(cst)) {
								modifiedCallback.run();
								super.visitLdcInsn(transformed.get());
							} else {
								super.visitLdcInsn(cst);
							}
						} else {
							super.visitLdcInsn(cst);
						}
					}
				};
			}
		});
	}

	protected abstract Optional<String> transformLdc(String input);
}
