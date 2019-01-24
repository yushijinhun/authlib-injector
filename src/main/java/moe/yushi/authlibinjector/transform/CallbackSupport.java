/*
 * Copyright (C) 2019  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import moe.yushi.authlibinjector.transform.TransformUnit.TransformContext;

public final class CallbackSupport {
	private CallbackSupport() {
	}

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

	private static final Handle BOOTSTRAP_METHOD = new Handle(
			H_INVOKESTATIC,
			Type.getInternalName(CallbackEntryPoint.class),
			"bootstrap",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;",
			false);

	public static void invoke(TransformContext ctx, MethodVisitor mv, Class<?> owner, String methodName) {
		ctx.requireMinimumClassVersion(50);
		ctx.upgradeClassVersion(51);

		String descriptor = Type.getMethodDescriptor(findCallbackMethod(owner, methodName));
		mv.visitInvokeDynamicInsn(methodName, descriptor, BOOTSTRAP_METHOD, owner.getName());
	}
}
