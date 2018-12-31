package moe.yushi.authlibinjector.transform;

import static org.objectweb.asm.Opcodes.ASM7;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import moe.yushi.authlibinjector.util.Logging;

public class AuthlibLogInterceptor implements TransformUnit {

	private static Set<ClassLoader> interceptedClassloaders = Collections.newSetFromMap(new WeakHashMap<>());

	public static void onClassLoading(ClassLoader classLoader) {
		Class<?> classLogManager;
		try {
			classLogManager = classLoader.loadClass("org.apache.logging.log4j.LogManager");
		} catch (ClassNotFoundException e) {
			return;
		}

		ClassLoader clLog4j = classLogManager.getClassLoader();
		synchronized (interceptedClassloaders) {
			if (!interceptedClassloaders.add(clLog4j)) {
				return;
			}
		}

		try {
			registerLogHandle(clLog4j);
			Logging.TRANSFORM.info("Registered log handler on " + clLog4j);
		} catch (Throwable e) {
			Logging.TRANSFORM.log(Level.WARNING, "Failed to register log handler on " + clLog4j, e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void registerLogHandle(ClassLoader cl) throws ReflectiveOperationException {
		final String appenderName = "AUTHLIB_INJECTOR_CONSOLE_APPENDER";
		final String loggerName = "AUTHLIB_INJECTOR_AUTHLIB_LOGGER";
		final String authlibPackageName = "com.mojang.authlib";

		Class<?> classLayout = cl.loadClass("org.apache.logging.log4j.core.Layout");
		Class<?> classAppender = cl.loadClass("org.apache.logging.log4j.core.Appender");
		Class<?> classAppenderRef = cl.loadClass("org.apache.logging.log4j.core.config.AppenderRef");
		Class<?> classLevel = cl.loadClass("org.apache.logging.log4j.Level");
		Class<?> classFilter = cl.loadClass("org.apache.logging.log4j.core.Filter");
		Class<?> classLoggerConfig = cl.loadClass("org.apache.logging.log4j.core.config.LoggerConfig");
		Class<?> classConfiguration = cl.loadClass("org.apache.logging.log4j.core.config.Configuration");
		Class<?> classPatternLayout = cl.loadClass("org.apache.logging.log4j.core.layout.PatternLayout");
		Class<?> classConsoleAppender = cl.loadClass("org.apache.logging.log4j.core.appender.ConsoleAppender");

		Object loggerContext = cl.loadClass("org.apache.logging.log4j.LogManager").getDeclaredMethod("getContext", boolean.class).invoke(null, false);
		Object configuration = cl.loadClass("org.apache.logging.log4j.core.LoggerContext").getMethod("getConfiguration").invoke(loggerContext);

		Object patternLayout;
		try {
			Object builder = classPatternLayout.getDeclaredMethod("newBuilder").invoke(null);
			Class<?> classBuilder = cl.loadClass("org.apache.logging.log4j.core.layout.PatternLayout$Builder");
			patternLayout = classBuilder.getMethod("build").invoke(builder);
		} catch (NoSuchMethodException ex) {
			// prior to https://github.com/apache/logging-log4j2/commit/7aabb11111c69f452d674b00845b66b82c80afa4
			Map<String, Object> values = new HashMap<>();
			values.put("alwaysWriteExceptions", true);
			values.put("noConsoleNoAnsi", true);
			patternLayout = invokeCreateMethod(classPatternLayout, "createLayout", configuration, values);
		}

		Object appender;
		try {
			Object buider = classConsoleAppender.getDeclaredMethod("newBuilder").invoke(null);
			Class<?> classBuilder = cl.loadClass("org.apache.logging.log4j.core.appender.ConsoleAppender$Builder");
			classBuilder.getMethod("withLayout", classLayout).invoke(buider, patternLayout);
			classBuilder.getMethod("withName", String.class).invoke(buider, appenderName);
			appender = classBuilder.getMethod("build").invoke(buider);
		} catch (NoSuchMethodException ex) {
			Map<String, Object> values = new HashMap<>();
			values.put("Layout", patternLayout);
			values.put("name", appenderName);
			values.put("follow", false);
			values.put("direct", false);
			values.put("ignoreExceptions", true);
			appender = invokeCreateMethod(classConsoleAppender, "createAppender", configuration, values);
		}
		classAppender.getMethod("start").invoke(appender);

		Object appenderRef;
		{
			Map<String, Object> values = new HashMap<>();
			values.put("ref", appenderName);
			appenderRef = invokeCreateMethod(classAppenderRef, "createAppenderRef", configuration, values);
		}
		Object appenderRefs = Array.newInstance(classAppenderRef, 1);
		Array.set(appenderRefs, 0, appenderRef);

		Object loggerConfig;
		{
			Map<String, Object> values = new HashMap<>();
			values.put("additivity", false);
			values.put("level", classLevel.getDeclaredField("ALL").get(null));
			values.put("name", loggerName);
			values.put("includeLocation", authlibPackageName);
			values.put("AppenderRef", appenderRefs);
			loggerConfig = invokeCreateMethod(classLoggerConfig, "createLogger", configuration, values);
		}

		classLoggerConfig.getMethod("addAppender", classAppender, classLevel, classFilter).invoke(loggerConfig, appender, null, null);
		try {
			classConfiguration.getMethod("addAppender", classAppender).invoke(configuration, appender);
		} catch (NoSuchMethodException e) {
			((Map) classConfiguration.getMethod("getAppenders").invoke(configuration)).put(appenderName, appender);
		}
		try {
			classConfiguration.getMethod("addLogger", String.class, classLoggerConfig).invoke(configuration, authlibPackageName, loggerConfig);
		} catch (NoSuchMethodException e) {
			// prior to https://github.com/apache/logging-log4j2/commit/f9744033b93e2fe1f52a27c66f24f53cf44939a2
			// bypass the check in https://github.com/apache/logging-log4j2/blob/log4j-2.0-beta9/log4j-core/src/main/java/org/apache/logging/log4j/core/config/BaseConfiguration.java#L554
			Class<?> classBaseConfiguration = cl.loadClass("org.apache.logging.log4j.core.config.BaseConfiguration");
			Field fieldLoggers = classBaseConfiguration.getDeclaredField("loggers");
			fieldLoggers.setAccessible(true);
			((Map) fieldLoggers.get(configuration)).put(authlibPackageName, loggerConfig);
			Method methodSetParents = classBaseConfiguration.getDeclaredMethod("setParents");
			methodSetParents.setAccessible(true);
			methodSetParents.invoke(configuration);
		}
		cl.loadClass("org.apache.logging.log4j.core.LoggerContext").getMethod("updateLoggers", classConfiguration).invoke(loggerContext, configuration);
	}

	private static Object invokeCreateMethod(Class<?> owner, String methodName, Object config, Map<String, Object> values) throws ReflectiveOperationException {
		ClassLoader cl = owner.getClassLoader();
		Class<?> classConfiguration = cl.loadClass("org.apache.logging.log4j.core.config.Configuration");
		@SuppressWarnings("unchecked")
		Class<? extends Annotation> classPluginAttribute = (Class<? extends Annotation>) cl.loadClass("org.apache.logging.log4j.core.config.plugins.PluginAttribute");
		Method methodPluginAttributeValue = classPluginAttribute.getMethod("value");
		@SuppressWarnings("unchecked")
		Class<? extends Annotation> classPluginElement = (Class<? extends Annotation>) cl.loadClass("org.apache.logging.log4j.core.config.plugins.PluginElement");
		Method methodPluginElementValue = classPluginElement.getMethod("value");
		@SuppressWarnings("unchecked")
		Class<? extends Annotation> classPluginFactory = (Class<? extends Annotation>) cl.loadClass("org.apache.logging.log4j.core.config.plugins.PluginFactory");

		Method method = Stream.of(owner.getDeclaredMethods())
				.filter(it -> it.getName().equals(methodName))
				.filter(it -> it.getDeclaredAnnotation(classPluginFactory) != null)
				.findFirst().orElseThrow(NoSuchMethodException::new);

		Object[] input = new Object[method.getParameterCount()];
		Parameter[] parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			Class<?> type = parameters[i].getType();
			String key = null;

			Annotation annotation = parameters[i].getDeclaredAnnotation(classPluginAttribute);
			if (annotation != null) {
				key = (String) methodPluginAttributeValue.invoke(annotation);
			} else {
				annotation = parameters[i].getDeclaredAnnotation(classPluginElement);
				if (annotation != null) {
					key = (String) methodPluginElementValue.invoke(annotation);
				}
			}

			if (key == null) {
				if (classConfiguration.isAssignableFrom(type)) {
					input[i] = config;
				}
			} else {
				Object value = values.get(key);
				if (value != null) {
					if (type.isPrimitive() || type.isInstance(value)) {
						input[i] = value;
					} else if (type == String.class) {
						input[i] = value.toString();
					}
				}
			}
		}

		return method.invoke(null, input);
	}

	@Override
	public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, Runnable modifiedCallback) {
		if (className.startsWith("com.mojang.authlib.")) {
			synchronized (interceptedClassloaders) {
				if (interceptedClassloaders.contains(classLoader)) {
					return Optional.empty();
				}
			}
			return Optional.of(new ClassVisitor(ASM7, writer) {

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
					if ("<clinit>".equals(name)) {
						mv.visitLdcInsn(Type.getType("L" + className.replace('.', '/') + ";"));
						mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
						mv.visitMethodInsn(INVOKESTATIC, AuthlibLogInterceptor.class.getName().replace('.', '/'), "onClassLoading", "(Ljava/lang/ClassLoader;)V", false);
						modifiedCallback.run();
					}
					return mv;
				}
			});
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "Authlib Log Interceptor";
	}
}
