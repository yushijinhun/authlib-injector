package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.F_APPEND;
import static org.objectweb.asm.Opcodes.F_CHOP;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class YggdrasilKeyTransformUnit implements TransformUnit {

	private static final List<PublicKey> PUBLIC_KEYS = new CopyOnWriteArrayList<>();

	public static List<PublicKey> getPublicKeys() {
		return PUBLIC_KEYS;
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM7, writer) {

				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					super.visit(version, access, name, signature, superName, interfaces);

					MethodVisitor mv = super.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
							"authlib_injector_isSignatureValid",
							"(Lcom/mojang/authlib/properties/Property;Ljava/security/PublicKey;)Z",
							null, null);
					mv.visitCode();
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ALOAD, 1);
					mv.visitMethodInsn(INVOKEVIRTUAL, "com/mojang/authlib/properties/Property", "isSignatureValid", "(Ljava/security/PublicKey;)Z", false);
					Label l0 = new Label();
					mv.visitJumpInsn(IFEQ, l0);
					mv.visitInsn(ICONST_1);
					mv.visitInsn(IRETURN);
					mv.visitLabel(l0);
					mv.visitFrame(F_SAME, 0, null, 0, null);
					mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(YggdrasilKeyTransformUnit.class), "getPublicKeys", "()Ljava/util/List;", false);
					mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
					mv.visitVarInsn(ASTORE, 2);
					Label l1 = new Label();
					mv.visitLabel(l1);
					mv.visitFrame(F_APPEND, 1, new Object[] { "java/util/Iterator" }, 0, null);
					mv.visitVarInsn(ALOAD, 2);
					mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
					Label l2 = new Label();
					mv.visitJumpInsn(IFEQ, l2);
					mv.visitVarInsn(ALOAD, 2);
					mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
					mv.visitTypeInsn(CHECKCAST, "java/security/PublicKey");
					mv.visitVarInsn(ASTORE, 3);
					mv.visitVarInsn(ALOAD, 0);
					mv.visitVarInsn(ALOAD, 3);
					mv.visitMethodInsn(INVOKEVIRTUAL, "com/mojang/authlib/properties/Property", "isSignatureValid", "(Ljava/security/PublicKey;)Z", false);
					Label l3 = new Label();
					mv.visitJumpInsn(IFEQ, l3);
					mv.visitInsn(ICONST_1);
					mv.visitInsn(IRETURN);
					mv.visitLabel(l3);
					mv.visitFrame(F_SAME, 0, null, 0, null);
					mv.visitJumpInsn(GOTO, l1);
					mv.visitLabel(l2);
					mv.visitFrame(F_CHOP, 1, null, 0, null);
					mv.visitInsn(ICONST_0);
					mv.visitInsn(IRETURN);
					mv.visitMaxs(2, 4);
					mv.visitEnd();
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					return new MethodVisitor(ASM7, super.visitMethod(access, name, desc, signature, exceptions)) {
						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
							if (opcode == INVOKEVIRTUAL
									&& "com/mojang/authlib/properties/Property".equals(owner)
									&& "isSignatureValid".equals(name)
									&& "(Ljava/security/PublicKey;)Z".equals(descriptor)) {
								modifiedCallback.run();
								super.visitMethodInsn(INVOKESTATIC,
										"com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService",
										"authlib_injector_isSignatureValid",
										"(Lcom/mojang/authlib/properties/Property;Ljava/security/PublicKey;)Z",
										false);
							} else {
								super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
							}
						}
					};
				}

			});
		} else {
			return Optional.empty();
		}
	}

	@Override
	public String toString() {
		return "Yggdrasil Public Key Transformer";
	}
}
