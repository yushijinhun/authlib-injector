/*
 * Copyright (C) 2020  Haowei Wen <yushijinhun@gmail.com> and contributors
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public final class CallbackSupport {
	private CallbackSupport() {
	}

	static final String METAFACTORY_NAME = "__authlibinjector_metafactory";
	static final String METAFACTORY_SIGNATURE = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

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

	public static void invoke(TransformContext ctx, MethodVisitor mv, Class<?> owner, String methodName) {
		ctx.requireMinimumClassVersion(50);
		ctx.upgradeClassVersion(51);

		String descriptor = Type.getMethodDescriptor(findCallbackMethod(owner, methodName));
		mv.visitInvokeDynamicInsn(methodName, descriptor, ctx.acquireCallbackMetafactory(), owner.getName());
	}
}
