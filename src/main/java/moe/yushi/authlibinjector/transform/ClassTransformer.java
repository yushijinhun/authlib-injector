package moe.yushi.authlibinjector.transform;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.util.ArrayList;
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
	public Set<String> ignores = new HashSet<>();
	public boolean debugSaveClass;

	private static class TransformHandle {

		private boolean modified = false;
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
					Logging.TRANSFORM.info("transform " + className + " using " + unit);
					modified = true;
					classBuffer = writer.toByteArray();
				}
			} else {
				pooledClassWriter = writer;
			}
		}

		public Optional<byte[]> getResult() {
			if (modified) {
				return Optional.of(classBuffer);
			} else {
				return Optional.empty();
			}
		}

	}

	@Override
	public byte[] transform(ClassLoader loader, String internalClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if (internalClassName != null && classfileBuffer != null) {
			try {
				String className = internalClassName.replace('/', '.');
				for (String prefix : ignores)
					if (className.startsWith(prefix)) return null;

				TransformHandle handle = new TransformHandle(className, classfileBuffer);
				units.forEach(handle::accept);
				if (handle.getResult().isPresent()) {
					byte[] classBuffer = handle.getResult().get();
					if (debugSaveClass) {
						saveClassFile(className, classBuffer);
					}
					return classBuffer;
				} else {
					Logging.TRANSFORM.fine("no transform performed on " + className);
					return null;
				}
			} catch (Throwable e) {
				Logging.TRANSFORM.log(Level.WARNING, "unable to transform: " + internalClassName, e);
			}
		}
		return null;
	}

	private void saveClassFile(String className, byte[] classBuffer) {
		try {
			Files.write(Paths.get(className + "_dump.class"), classBuffer, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			Logging.TRANSFORM.log(Level.WARNING, "unable to dump class " + className, e);
		}
	}

}
