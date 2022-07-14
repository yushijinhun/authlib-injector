/*
 * Copyright (C) 2022  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

final class CallbackSupport {
	private CallbackSupport() {
	}

	private static final String METAFACTORY_NAME = "__authlibinjector_metafactory";
	private static final String METAFACTORY_SIGNATURE = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

	private static Method findCallbackMethod(Class<?> owner, String methodName) {
		for (Method method : owner.getDeclaredMethods()) {
			int modifiers = method.getModifiers();
			if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) &&
					methodName.equals(method.getName()) &&
					method.getAnnotation(CallbackMethod.class) != null) {
				return method;
			}
		}
		throw new IllegalArgumentException("No such method: " + methodName);
	}

	static void callWithInvokeDynamic(MethodVisitor mv, Class<?> owner, String methodName, TransformContext ctx) {
		String descriptor = Type.getMethodDescriptor(findCallbackMethod(owner, methodName));
		Handle callbackMetafactory = new Handle(
				H_INVOKESTATIC,
				ctx.getClassName().replace('.', '/'),
				CallbackSupport.METAFACTORY_NAME,
				CallbackSupport.METAFACTORY_SIGNATURE,
				ctx.isInterface());
		mv.visitInvokeDynamicInsn(methodName, descriptor, callbackMetafactory, owner.getName());
	}

	static void callWithIntermediateMethod(MethodVisitor mv0, Class<?> owner, String methodName, TransformContext ctx) {
		Method callbackMethod = findCallbackMethod(owner, methodName);
		String descriptor = Type.getMethodDescriptor(callbackMethod);
		String intermediateMethod = "__authlibinjector_intermediate__" + owner.getName().replace('.', '_') + "__" + methodName;
		mv0.visitMethodInsn(INVOKESTATIC, ctx.getClassName().replace('.', '/'), intermediateMethod, descriptor, ctx.isInterface());

		ctx.addGeneratedMethod(intermediateMethod, cv -> {
			int paramNum = callbackMethod.getParameterCount();
			Class<?>[] paramTypes = callbackMethod.getParameterTypes();
			Class<?> returnType = callbackMethod.getReturnType();

			MethodVisitor mv = cv.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, intermediateMethod, descriptor, null, null);
			mv.visitCode();
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "publicLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;", false);
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false);
			mv.visitLdcInsn(owner.getName());
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
			mv.visitLdcInsn(methodName);
			pushType(mv, returnType);
			mv.visitLdcInsn(paramNum);
			mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
			for (int i = 0; i < paramNum; i++) {
				mv.visitInsn(DUP);
				mv.visitLdcInsn(i);
				pushType(mv, paramTypes[i]);
				mv.visitInsn(AASTORE);
			}
			mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
			for (int i = 0; i < paramNum; i++) {
				Class<?> type = paramTypes[i];
				if (type == boolean.class || type == byte.class || type == char.class || type == short.class || type == int.class) {
					mv.visitVarInsn(ILOAD, i);
				} else if (type == long.class) {
					mv.visitVarInsn(LLOAD, i);
				} else if (type == float.class) {
					mv.visitVarInsn(FLOAD, i);
				} else if (type == double.class) {
					mv.visitVarInsn(DLOAD, i);
				} else {
					mv.visitVarInsn(ALOAD, i);
				}
			}
			mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", descriptor, false);
			if (returnType == void.class) {
				mv.visitInsn(RETURN);
			} else if (returnType == boolean.class || returnType == byte.class || returnType == char.class || returnType == short.class || returnType == int.class) {
				mv.visitInsn(IRETURN);
			} else if (returnType == long.class) {
				mv.visitInsn(LRETURN);
			} else if (returnType == float.class) {
				mv.visitInsn(FRETURN);
			} else if (returnType == double.class) {
				mv.visitInsn(DRETURN);
			} else {
				mv.visitInsn(ARETURN);
			}
			mv.visitMaxs(-1, -1);
			mv.visitEnd();
		});
	}

	private static void pushType(MethodVisitor mv, Class<?> type) {
		if (type.isPrimitive()) {
			if (type == boolean.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
			} else if (type == byte.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
			} else if (type == char.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
			} else if (type == short.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
			} else if (type == int.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
			} else if (type == float.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
			} else if (type == long.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
			} else if (type == double.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
			} else if (type == void.class) {
				mv.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
			}
		} else {
			mv.visitLdcInsn(Type.getType(type));
		}
	}

	static void insertMetafactory(ClassVisitor visitor) {
		MethodVisitor mv = visitor.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC,
				CallbackSupport.METAFACTORY_NAME,
				CallbackSupport.METAFACTORY_SIGNATURE,
				null, null);
		mv.visitCode();
		mv.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(INVOKESTATIC, "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false);
		mv.visitVarInsn(ALOAD, 3);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
		mv.visitVarInsn(ALOAD, 1);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
		mv.visitInsn(ARETURN);
		mv.visitMaxs(-1, -1);
		mv.visitEnd();
	}
}
