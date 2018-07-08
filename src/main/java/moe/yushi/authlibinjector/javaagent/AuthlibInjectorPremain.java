package moe.yushi.authlibinjector.javaagent;

import static moe.yushi.authlibinjector.AuthlibInjector.PROP_API_ROOT;
import static moe.yushi.authlibinjector.AuthlibInjector.bootstrap;
import static moe.yushi.authlibinjector.AuthlibInjector.nonTransformablePackages;
import static moe.yushi.authlibinjector.util.LoggingUtils.debug;
import static moe.yushi.authlibinjector.util.LoggingUtils.info;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;

public class AuthlibInjectorPremain {

	public static void premain(String arg, Instrumentation instrumentation) {
		try {
			info("launched from premain");
			initInjector(arg, instrumentation, false);
		} catch (Throwable e) {
			// prevent the exception being thrown to VM
			e.printStackTrace();
			info("an exception has been caught, exiting");
			System.exit(1);
		}
	}

	public static void agentmain(String arg, Instrumentation instrumentation) {
		try {
			info("launched from agentmain");
			initInjector(arg, instrumentation, true);
		} catch (Throwable e) {
			// prevent the exception being thrown to VM
			e.printStackTrace();
		}
	}

	public static void initInjector(String arg, Instrumentation instrumentation, boolean needsRetransform) {
		setupConfig(arg);

		boolean retransformSupported = instrumentation.isRetransformClassesSupported();
		boolean retransformEnabled = retransformSupported && needsRetransform;
		bootstrap(x -> instrumentation.addTransformer(x, retransformEnabled));

		if (needsRetransform) {
			if (retransformSupported) {
				info("start retransforming");
				doRetransform(instrumentation);
			} else {
				info("retransforming is not supported");
			}
		}
	}

	private static void setupConfig(String arg) {
		if (arg != null && !arg.isEmpty()) {
			System.setProperty(PROP_API_ROOT, arg);
		}
	}

	private static void doRetransform(Instrumentation instrumentation) {
		try {
			long t0 = System.currentTimeMillis();
			Class<?>[] classToRetransform = getClassesToRetransform(instrumentation);
			if (classToRetransform.length > 0) {
				instrumentation.retransformClasses(classToRetransform);
			}
			long t1 = System.currentTimeMillis();
			info("retransforming finished in {0}ms", t1 - t0);
		} catch (Throwable e) {
			info("unable to retransform");
			e.printStackTrace();
		}
	}

	private static Class<?>[] getClassesToRetransform(Instrumentation instrumentation) {
		Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
		Class<?>[] dest = new Class[loadedClasses.length];
		int idx = 0;
		for (Class<?> clazz : loadedClasses) {
			if (instrumentation.isModifiableClass(clazz)) {
				boolean toRetransform = true;
				for (String nonTransformablePackage : nonTransformablePackages) {
					if (clazz.getName().startsWith(nonTransformablePackage)) {
						toRetransform = false;
						break;
					}
				}
				if (toRetransform) {
					dest[idx++] = clazz;
				}
			}
		}
		debug("loaded {0} classes, {1} to retransform", loadedClasses.length, idx);
		return Arrays.copyOf(dest, idx);
	}

}
