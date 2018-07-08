package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ASM6;
import static org.objectweb.asm.Opcodes.BASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.T_BYTE;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class YggdrasilKeyTransformUnit implements TransformUnit {

	private byte[] publicKey;

	public YggdrasilKeyTransformUnit(byte[] publicKey) {
		this.publicKey = publicKey;
	}

	@Override
	public Optional<ClassVisitor> transform(String className, ClassVisitor writer, Runnable modifiedCallback) {
		if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM6, writer) {

				@Override
				public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
					if ("<init>".equals(name)) {
						return new MethodVisitor(ASM6, super.visitMethod(access, name, desc, signature, exceptions)) {

							int state = 0;

							@Override
							public void visitLdcInsn(Object cst) {
								if (state == 0 && cst instanceof Type && ((Type) cst).getInternalName().equals("com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService")) {
									state++;
								} else if (state == 1 && "/yggdrasil_session_pubkey.der".equals(cst)) {
									state++;
								} else {
									super.visitLdcInsn(cst);
								}
							}

							@Override
							public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
								if (state == 2 && opcode == INVOKEVIRTUAL && "java/lang/Class".equals(owner) && "getResourceAsStream".equals(name) && "(Ljava/lang/String;)Ljava/io/InputStream;".equals(desc)) {
									state++;
								} else if (state == 3 && opcode == INVOKESTATIC && "org/apache/commons/io/IOUtils".equals(owner) && "toByteArray".equals(name) && "(Ljava/io/InputStream;)[B".equals(desc)) {
									state++;
									if (state == 4) {
										modifiedCallback.run();
										super.visitIntInsn(SIPUSH, publicKey.length);
										super.visitIntInsn(NEWARRAY, T_BYTE);
										for (int i = 0; i < publicKey.length; i++) {
											super.visitInsn(DUP);
											super.visitIntInsn(SIPUSH, i);
											super.visitIntInsn(BIPUSH, publicKey[i]);
											super.visitInsn(BASTORE);
										}
									}
								} else {
									super.visitMethodInsn(opcode, owner, name, desc, itf);
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
		return "yggdrasil-publickey-transform";
	}

}
