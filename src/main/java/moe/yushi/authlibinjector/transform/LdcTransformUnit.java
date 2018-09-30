package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ASM6;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.util.Logging;

public abstract class LdcTransformUnit implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(String className, ClassVisitor writer, Runnable modifiedCallback) {
		return Optional.of(new ClassVisitor(ASM6, writer) {

			@Override
			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				return new MethodVisitor(ASM6, super.visitMethod(access, name, desc, signature, exceptions)) {

					@Override
					public void visitLdcInsn(Object cst) {
						if (cst instanceof String) {
							Optional<String> transformed = transformLdc((String) cst);
							if (transformed.isPresent() && !transformed.get().equals(cst)) {
								modifiedCallback.run();
								Logging.TRANSFORM.fine("Transformed string [" + cst + "] to [" + transformed.get() + "]");
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
