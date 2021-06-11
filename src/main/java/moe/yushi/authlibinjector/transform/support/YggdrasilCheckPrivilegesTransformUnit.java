package moe.yushi.authlibinjector.transform.support;

import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Optional;

import static org.objectweb.asm.Opcodes.*;

public class YggdrasilCheckPrivilegesTransformUnit implements TransformUnit {

    @CallbackMethod
    public static boolean returnTrue() {
        return true;
    }

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
        if ("com.mojang.authlib.yggdrasil.YggdrasilSocialInteractionsService".equals(className)) {
            return Optional.of(new ClassVisitor(ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if ("checkPrivileges".equals(name)) {
                        ctx.markModified();
                        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitInsn(RETURN);
                        mv.visitMaxs(-1, -1);
                        mv.visitEnd();
                        return null;
                    } else if ("serversAllowed".equals(name) || "chatAllowed".equals(name) || "telemetryAllowed".equals(name) || "realmsAllowed".equals(name)) {
                        ctx.markModified();
                        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                        mv.visitVarInsn(ALOAD, 0);
                        CallbackSupport.invoke(ctx, mv, YggdrasilCheckPrivilegesTransformUnit.class, "returnTrue");
                        mv.visitInsn(IRETURN);
                        mv.visitMaxs(-1, -1);
                        mv.visitEnd();
                        return null;
                    } else {
                        return super.visitMethod(access, name, desc, signature, exceptions);
                    }
                }
            });
        }
        return Optional.empty();
    }
}
