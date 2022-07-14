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

import static java.util.Collections.emptyList;
import static moe.yushi.authlibinjector.util.Logging.log;
import static moe.yushi.authlibinjector.util.Logging.Level.DEBUG;
import static moe.yushi.authlibinjector.util.Logging.Level.INFO;
import static moe.yushi.authlibinjector.util.Logging.Level.WARNING;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ASM9;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.Config;

public class ClassTransformer implements ClassFileTransformer {

	public final List<TransformUnit> units = new CopyOnWriteArrayList<>();
	public final List<ClassLoadingListener> listeners = new CopyOnWriteArrayList<>();
	public final PerformanceMetrics performanceMetrics = new PerformanceMetrics();
	private String[] ignores = new String[0];

	private class TransformHandle {

		private class TransformContextImpl implements TransformContext {

			public boolean modifiedMark;
			public boolean callbackMetafactoryRequested = false;

			@Override
			public void markModified() {
				modifiedMark = true;
			}

			@Override
			public List<String> getStringConstants() {
				return TransformHandle.this.getStringConstants();
			}

			@Override
			public String getClassName() {
				return className;
			}

			@Override
			public boolean isInterface() {
				return TransformHandle.this.isInterface();
			}

			@Override
			public void invokeCallback(MethodVisitor mv, Class<?> owner, String methodName) {
				boolean useInvokeDynamic = (getClassVersion() & 0xffff) >= 50;

				if (useInvokeDynamic) {
					addCallbackMetafactory = true;
					CallbackSupport.callWithInvokeDynamic(mv, owner, methodName, this);
				} else {
					CallbackSupport.callWithIntermediateMethod(mv, owner, methodName, this);
				}
			}

			@Override
			public void addGeneratedMethod(String name, Consumer<ClassVisitor> generator) {
				if (generatedMethods == null) {
					generatedMethods = new LinkedHashMap<>();
				}
				generatedMethods.put(name, generator);
			}
		}

		private final String className;
		private final ClassLoader classLoader;
		private byte[] classBuffer;
		private ClassReader cachedClassReader;
		private List<String> cachedConstants;

		private List<TransformUnit> appliedTransformers;
		private boolean addCallbackMetafactory = false;
		private Map<String, Consumer<ClassVisitor>> generatedMethods;

		public TransformHandle(ClassLoader classLoader, String className, byte[] classBuffer) {
			this.className = className;
			this.classBuffer = classBuffer;
			this.classLoader = classLoader;
		}

		private ClassReader getClassReader() {
			if (cachedClassReader == null)
				cachedClassReader = new ClassReader(classBuffer);
			return cachedClassReader;
		}

		private boolean isInterface() {
			return (getClassReader().getAccess() & ACC_INTERFACE) != 0;
		}

		private List<String> getStringConstants() {
			if (cachedConstants == null)
				cachedConstants = extractStringConstants(getClassReader());
			return cachedConstants;
		}

		private int getClassVersion() {
			ClassReader reader = getClassReader();
			return reader.readInt(reader.getItem(1) - 7);
		}

		public void accept(TransformUnit... units) {
			long t0 = System.nanoTime();

			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

			TransformContextImpl[] ctxs = new TransformContextImpl[units.length];
			ClassVisitor chain = writer;
			for (int i = units.length - 1; i >= 0; i--) {
				TransformContextImpl ctx = new TransformContextImpl();
				Optional<ClassVisitor> visitor = units[i].transform(classLoader, className, chain, ctx);
				if (!visitor.isPresent())
					continue;
				ctxs[i] = ctx;
				chain = visitor.get();
			}

			long t1 = System.nanoTime();
			synchronized (performanceMetrics) {
				performanceMetrics.scanTime += t1 - t0;
			}

			if (chain == writer)
				return;

			t0 = System.nanoTime();

			getClassReader().accept(chain, 0);

			t1 = System.nanoTime();
			synchronized (performanceMetrics) {
				performanceMetrics.analysisTime += t1 - t0;
			}

			boolean modified = false;
			for (int i = 0; i < units.length; i++) {
				TransformContextImpl ctx = ctxs[i];
				if (ctx == null || !ctx.modifiedMark)
					continue;

				log(INFO, "Transformed [" + className + "] with [" + units[i] + "]");

				if (appliedTransformers == null)
					appliedTransformers = new ArrayList<>();
				appliedTransformers.add(units[i]);

				this.addCallbackMetafactory |= ctx.callbackMetafactoryRequested;

				modified = true;
			}

			if (modified) {
				updateClassBuffer(writer.toByteArray());
			}
		}

		private void injectCallbackMetafactory() {
			log(DEBUG, "Adding callback metafactory");

			int classVersion = getClassVersion();
			int majorVersion = classVersion & 0xffff;

			int newVersion;
			if (majorVersion < 51) {
				newVersion = 51;
				log(DEBUG, "Upgrading class version from " + classVersion + " to " + newVersion);
			} else {
				newVersion = classVersion;
			}

			ClassReader reader = getClassReader();
			ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
			ClassVisitor visitor = new ClassVisitor(ASM9, writer) {
				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					super.visit(newVersion, access, name, signature, superName, interfaces);
					CallbackSupport.insertMetafactory(this);
				}
			};
			reader.accept(visitor, 0);
			updateClassBuffer(writer.toByteArray());
		}

		private void injectGeneratedMethods() {
			ClassReader reader = getClassReader();
			ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
			ClassVisitor visitor = new ClassVisitor(ASM9, writer) {
				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					super.visit(version, access, name, signature, superName, interfaces);
					for (Entry<String, Consumer<ClassVisitor>> el : generatedMethods.entrySet()) {
						log(DEBUG, "Adding generated method [" + el.getKey() + "]");
						el.getValue().accept(this);
					}
				}
			};
			reader.accept(visitor, 0);
			updateClassBuffer(writer.toByteArray());
		}

		private void updateClassBuffer(byte[] buf) {
			classBuffer = buf;
			cachedClassReader = null;
			cachedConstants = null;
		}

		public Optional<byte[]> finish() {
			if (appliedTransformers == null || appliedTransformers.isEmpty()) {
				return Optional.empty();
			}
			if (addCallbackMetafactory) {
				injectCallbackMetafactory();
			}
			if (generatedMethods != null) {
				injectGeneratedMethods();
			}
			return Optional.of(classBuffer);
		}

		public List<TransformUnit> getAppliedTransformers() {
			return appliedTransformers == null ? emptyList() : appliedTransformers;
		}

		public byte[] getFinalResult() {
			return classBuffer;
		}
	}

	@Override
	public byte[] transform(ClassLoader loader, String internalClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if (internalClassName != null && classfileBuffer != null) {
			try {
				long t0 = System.nanoTime();

				String className = internalClassName.replace('/', '.');
				for (String ignore : ignores) {
					if (className.startsWith(ignore)) {
						listeners.forEach(it -> it.onClassLoading(loader, className, classfileBuffer, Collections.emptyList()));

						long t1 = System.nanoTime();
						synchronized (performanceMetrics) {
							performanceMetrics.classesSkipped++;
							performanceMetrics.totalTime += t1 - t0;
							performanceMetrics.matchTime += t1 - t0;
						}
						return null;
					}
				}
				long t1 = System.nanoTime();

				TransformHandle handle = new TransformHandle(loader, className, classfileBuffer);
				TransformUnit[] unitsArray = units.toArray(new TransformUnit[0]);
				handle.accept(unitsArray);

				Optional<byte[]> transformResult = handle.finish();
				if (Config.printUntransformedClass && !transformResult.isPresent()) {
					log(DEBUG, "No transformation is applied to [" + className + "]");
				}

				listeners.forEach(it -> it.onClassLoading(loader, className, handle.getFinalResult(), handle.getAppliedTransformers()));

				long t2 = System.nanoTime();

				synchronized (performanceMetrics) {
					performanceMetrics.classesScanned++;
					performanceMetrics.totalTime += t2 - t0;
					performanceMetrics.matchTime += t1 - t0;
				}

				return transformResult.orElse(null);
			} catch (Throwable e) {
				log(WARNING, "Failed to transform [" + internalClassName + "]", e);
			}
		}
		return null;
	}

	private static List<String> extractStringConstants(ClassReader reader) {
		List<String> constants = new ArrayList<>();
		int constantPoolSize = reader.getItemCount();
		char[] buf = new char[reader.getMaxStringLength()];
		for (int idx = 1; idx < constantPoolSize; idx++) {
			int offset = reader.getItem(idx);
			if (offset == 0)
				continue;
			int type = reader.readByte(offset - 1);
			if (type == 8) { // CONSTANT_String_info
				String constant = (String) reader.readConst(idx, buf);
				constants.add(constant);
			}
		}
		return constants;
	}

	public void setIgnores(Collection<String> newIgnores) {
		ignores = newIgnores.toArray(ignores);
	}
}
