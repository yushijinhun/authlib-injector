/*
 * Copyright (C) 2021  Haowei Wen <yushijinhun@gmail.com> and contributors
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
package moe.yushi.authlibinjector.transform.support;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM9;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

/**
 * See <https://github.com/yushijinhun/authlib-injector/issues/126>
 */
public class ConcatenateURLTransformUnit implements TransformUnit {

	@CallbackMethod
	public static URL concatenateURL(URL url, String query) {
		try {
			if (url.getQuery() != null && url.getQuery().length() > 0) {
				return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + "&" + query);
			} else {
				return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + "?" + query);
			}
		} catch (MalformedURLException ex) {
			throw new IllegalArgumentException("Could not concatenate given URL with GET arguments!", ex);
		}
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
		if ("com.mojang.authlib.HttpAuthenticationService".equals(className)) {
			return Optional.of(new ClassVisitor(ASM9, writer) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if ("concatenateURL".equals(name) && "(Ljava/net/URL;Ljava/lang/String;)Ljava/net/URL;".equals(descriptor)) {
						ctx.markModified();
						MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
						mv.visitCode();
						mv.visitVarInsn(ALOAD, 0);
						mv.visitVarInsn(ALOAD, 1);
						CallbackSupport.invoke(ctx, mv, ConcatenateURLTransformUnit.class, "concatenateURL");
						mv.visitInsn(ARETURN);
						mv.visitMaxs(-1, -1);
						mv.visitEnd();
						return null;
					} else {
						return super.visitMethod(access, name, descriptor, signature, exceptions);
					}
				}
			});
		} else {
			return Optional.empty();
		}
	}

	@Override
	public String toString() {
		return "ConcatenateURL Workaround";
	}
}
