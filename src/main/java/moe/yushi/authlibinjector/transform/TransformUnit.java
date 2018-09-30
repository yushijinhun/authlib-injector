package moe.yushi.authlibinjector.transform;

import java.util.Optional;
import org.objectweb.asm.ClassVisitor;

public interface TransformUnit {

	Optional<ClassVisitor> transform(String className, ClassVisitor writer, Runnable modifiedCallback);

}
