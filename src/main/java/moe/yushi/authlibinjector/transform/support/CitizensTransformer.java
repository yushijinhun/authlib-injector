package moe.yushi.authlibinjector.transform.support;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.RETURN;

import java.util.Optional;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import moe.yushi.authlibinjector.transform.TransformUnit;

/**
 * Support for Citizens2
 *
 * In <https://github.com/CitizensDev/Citizens2/commit/28b0c4fdc3b343d4dc14f2a45cff37c0b75ced1d>,
 * the profile-url that Citizens use became configurable. This class is used to make Citizens ignore
 * the config property and use authlib-injector's url.
 */
public class CitizensTransformer implements TransformUnit {

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("net.citizensnpcs.Settings$Setting".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if (("loadFromKey".equals(name) || "setAtKey".equals(name))
							&& "(Lnet/citizensnpcs/api/util/DataKey;)V".equals(descriptor)) {
						return new MethodVisitor(ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
							@Override
							public void visitCode() {
								super.visitCode();
								super.visitLdcInsn("general.authlib.profile-url");
								super.visitVarInsn(ALOAD, 0);
								super.visitFieldInsn(GETFIELD, "net/citizensnpcs/Settings$Setting", "path", "Ljava/lang/String;");
								super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
								Label lbl = new Label();
								super.visitJumpInsn(IFEQ, lbl);
								super.visitInsn(RETURN);
								super.visitLabel(lbl);
								super.visitFrame(F_SAME, 0, null, 0, null);
								modifiedCallback.run();
							}
						};
					}
					return super.visitMethod(access, name, descriptor, signature, exceptions);
				}
			});
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "Citizens2 Support";
	}
}
