package org.to2mbn.authlibinjector.tweaker;

import static org.to2mbn.authlibinjector.AuthlibInjector.bootstrap;
import static org.to2mbn.authlibinjector.AuthlibInjector.log;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class AuthlibInjectorTweaker implements ITweaker {

	static List<ClassFileTransformer> transformers = new ArrayList<>();

	@Override
	public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader launchClassLoader) {
		ClassLoader originalCtxCl = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		try {
			log("launched from tweaker");
			bootstrap(transformers::add);
			launchClassLoader.registerTransformer(TweakerTransformerAdapter.class.getName());
		} finally {
			Thread.currentThread().setContextClassLoader(originalCtxCl);
		}
	}

	@Override
	public String getLaunchTarget() {
		return "net.minecraft.client.main.Main";
	}

	@Override
	public String[] getLaunchArguments() {
		return new String[0];
	}
}
