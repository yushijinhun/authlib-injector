package moe.yushi.authlibinjector.transform.support;

import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.Optional;

import static org.objectweb.asm.Opcodes.*;

/**
 * Support for BungeeCord and downstream branches
 * <p>
 * BungeeCord limited the player name character in <https://github.com/SpigotMC/BungeeCord/blob/c7b0c3cd48c9929c6ba41ff333727adba89b4e07/proxy/src/main/java/net/md_5/bungee/util/AllowedCharacters.java#L28>
 * caused all non-ASCII characters profile can not join the server.
 * This class is used to replace the original method to allow all characters in offline mode.
 */
public class BungeeCordTransformer implements TransformUnit {

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext context) {
        if ("net.md_5.bungee.util.AllowedCharacters".equals(className)) {
            return Optional.of(new ClassVisitor(ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // private static boolean isNameAllowedCharacter(char c, boolean onlineMode)
                    if ("isNameAllowedCharacter".equals(name) && "(CZ)Z".equals(descriptor)) {
                        return new MethodVisitor(ASM9, new ClassWriter(ClassWriter.COMPUTE_FRAMES).visitMethod(access, name, descriptor, signature, exceptions)) {
                            @Override
                            public void visitCode() {
                                // return isChatAllowedCharacter( c ) && c != ' ';
                                super.visitCode();
                                // ILOAD c
                                super.visitVarInsn(ILOAD, 0);
                                // INVOKESTATIC net/md_5/bungee/util/AllowedCharacters.isChatAllowedCharacter(C)Z
                                super.visitMethodInsn(INVOKESTATIC, "net/md_5/bungee/util/AllowedCharacters", "isChatAllowedCharacter", "(C)Z", false);
                                // IFEQ J
                                Label falseLabel = new Label();
                                super.visitJumpInsn(IFEQ, falseLabel);
                                // ILOAD c
                                super.visitVarInsn(ILOAD, 0);
                                // BIPUSH 32
                                super.visitVarInsn(BIPUSH, 32);
                                // IF_ICMPEQ J
                                super.visitJumpInsn(IFEQ, falseLabel);
                                // ICONST_1
                                super.visitInsn(ICONST_1);
                                // GOTO K
                                Label returnLabel = new Label();
                                super.visitJumpInsn(GOTO, returnLabel);
                                // J:
                                super.visitLabel(falseLabel);
                                // ICONST_0
                                super.visitInsn(ICONST_0);
                                // K:
                                super.visitLabel(returnLabel);
                                // IRETURN
                                super.visitInsn(IRETURN);

                                super.visitEnd();

                                context.markModified();
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
        return "BungeeCord Support";
    }
}
