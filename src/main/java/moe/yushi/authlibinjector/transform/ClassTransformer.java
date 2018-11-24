package moe.yushi.authlibinjector.transform;

import static java.util.Collections.emptyList;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import moe.yushi.authlibinjector.util.Logging;

public class ClassTransformer implements ClassFileTransformer {

	public List<TransformUnit> units = new ArrayList<>();
	public List<ClassLoadingListener> listeners = new ArrayList<>();
	public Set<String> ignores = new HashSet<>();
	public boolean debugSaveClass;

	private static class TransformHandle {

		private List<TransformUnit> appliedTransformers;
		private boolean currentModified;
		private String className;
		private byte[] classBuffer;
		private ClassWriter pooledClassWriter;

		public TransformHandle(String className, byte[] classBuffer) {
			this.className = className;
			this.classBuffer = classBuffer;
		}

		public void accept(TransformUnit unit) {
			ClassWriter writer;
			if (pooledClassWriter == null) {
				writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			} else {
				writer = pooledClassWriter;
				pooledClassWriter = null;
			}

			Optional<ClassVisitor> optionalVisitor = unit.transform(className, writer, () -> currentModified = true);
			if (optionalVisitor.isPresent()) {
				currentModified = false;
				ClassReader reader = new ClassReader(classBuffer);
				reader.accept(optionalVisitor.get(), 0);
				if (currentModified) {
					Logging.TRANSFORM.info("Transformed [" + className + "] with [" + unit + "]");
					if (appliedTransformers == null) {
						appliedTransformers = new ArrayList<>();
					}
					appliedTransformers.add(unit);
					classBuffer = writer.toByteArray();
				}
			} else {
				pooledClassWriter = writer;
			}
		}

		public Optional<byte[]> getTransformResult() {
			if (appliedTransformers == null || appliedTransformers.isEmpty()) {
				return Optional.empty();
			} else {
				return Optional.of(classBuffer);
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

				TransformHandle handle = new TransformHandle(className, classfileBuffer);
				units.forEach(handle::accept);
				listeners.forEach(it -> it.onClassLoading(loader, className, handle.getFinalResult(), handle.getAppliedTransformers()));

				if (handle.getTransformResult().isPresent()) {
					byte[] classBuffer = handle.getTransformResult().get();
					if (debugSaveClass) {
						saveClassFile(className, classBuffer);
					}
					return classBuffer;
				} else {
					Logging.TRANSFORM_SKIPPED.fine("No transformation is applied to [" + className + "]");
					return null;
				}
			} catch (Throwable e) {
				Logging.TRANSFORM.log(Level.WARNING, "Failed to transform [" + internalClassName + "]", e);
			}
		}
		return null;
	}

	private void saveClassFile(String className, byte[] classBuffer) {
		Path dumpFile = Paths.get(className + "_dump.class");
		try {
			Files.write(dumpFile, classBuffer, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			Logging.TRANSFORM.info("Transformed class is dumped to [" + dumpFile + "]");
		} catch (IOException e) {
			Logging.TRANSFORM.log(Level.WARNING, "Failed to dump class [" + className + "]", e);
		}
	}

}
