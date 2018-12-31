package moe.yushi.authlibinjector.transform;

import java.util.Optional;
import org.objectweb.asm.ClassVisitor;

public interface TransformUnit {

	Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback);

}
