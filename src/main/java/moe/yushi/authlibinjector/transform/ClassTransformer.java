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

import static java.util.Collections.emptyList;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.util.Logging;

public class ClassTransformer implements ClassFileTransformer {

	private static final boolean PRINT_UNTRANSFORMED_CLASSES = Boolean.getBoolean(AuthlibInjector.PROP_PRINT_UNTRANSFORMED_CLASSES);

	public final List<TransformUnit> units = new CopyOnWriteArrayList<>();
	public final List<ClassLoadingListener> listeners = new CopyOnWriteArrayList<>();
	public final Set<String> ignores = Collections.newSetFromMap(new ConcurrentHashMap<>());

	private static class TransformContextImpl implements TransformContext {

		public boolean modifiedMark;
		public int minVersionMark = -1;
		public int upgradedVersionMark = -1;

		@Override
		public void markModified() {
			modifiedMark = true;
		}

		@Override
		public void requireMinimumClassVersion(int version) {
			if (this.minVersionMark < version) {
				this.minVersionMark = version;
			}
		}

		@Override
		public void upgradeClassVersion(int version) {
			if (this.upgradedVersionMark < version) {
				this.upgradedVersionMark = version;
			}
		}
	}

	private static class TransformHandle {

		private final String className;
		private final ClassLoader classLoader;
		private byte[] classBuffer;

		private List<TransformUnit> appliedTransformers;
		private int minVersion = -1;
		private int upgradedVersion = -1;

		public TransformHandle(ClassLoader classLoader, String className, byte[] classBuffer) {
			this.className = className;
			this.classBuffer = classBuffer;
			this.classLoader = classLoader;
		}

		public void accept(TransformUnit unit) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			TransformContextImpl ctx = new TransformContextImpl();

			Optional<ClassVisitor> optionalVisitor = unit.transform(classLoader, className, writer, ctx);
			if (optionalVisitor.isPresent()) {
				ClassReader reader = new ClassReader(classBuffer);
				reader.accept(optionalVisitor.get(), 0);
				if (ctx.modifiedMark) {
					Logging.TRANSFORM.info("Transformed [" + className + "] with [" + unit + "]");
					if (appliedTransformers == null) {
						appliedTransformers = new ArrayList<>();
					}
					appliedTransformers.add(unit);
					classBuffer = writer.toByteArray();
					if (ctx.minVersionMark > this.minVersion) {
						this.minVersion = ctx.minVersionMark;
					}
					if (ctx.upgradedVersionMark > this.upgradedVersion) {
						this.upgradedVersion = ctx.upgradedVersionMark;
					}
				}
			}
		}

		public Optional<byte[]> finish() {
			if (appliedTransformers == null || appliedTransformers.isEmpty()) {
				return Optional.empty();
			} else {
				if (minVersion == -1 && upgradedVersion == -1) {
					return Optional.of(classBuffer);
				} else {
					try {
						accept(new ClassVersionTransformUnit(minVersion, upgradedVersion));
						return Optional.of(classBuffer);
					} catch (ClassVersionException e) {
						Logging.TRANSFORM.warning("Skipping [" + className + "], " + e.getMessage());
						return Optional.empty();
					}
				}
			}
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
				String className = internalClassName.replace('/', '.');
				for (String prefix : ignores) {
					if (className.startsWith(prefix)) {
						listeners.forEach(it -> it.onClassLoading(loader, className, classfileBuffer, Collections.emptyList()));
						return null;
					}
				}

				TransformHandle handle = new TransformHandle(loader, className, classfileBuffer);
				units.forEach(handle::accept);
				listeners.forEach(it -> it.onClassLoading(loader, className, handle.getFinalResult(), handle.getAppliedTransformers()));

				Optional<byte[]> transformResult = handle.finish();
				if (PRINT_UNTRANSFORMED_CLASSES && !transformResult.isPresent()) {
					Logging.TRANSFORM.fine("No transformation is applied to [" + className + "]");
				}
				return transformResult.orElse(null);
			} catch (Throwable e) {
				Logging.TRANSFORM.log(Level.WARNING, "Failed to transform [" + internalClassName + "]", e);
			}
		}
		return null;
	}
}
