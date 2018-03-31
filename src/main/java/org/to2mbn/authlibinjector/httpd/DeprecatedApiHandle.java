package org.to2mbn.authlibinjector.httpd;

import static org.to2mbn.authlibinjector.AuthlibInjector.info;
import java.io.IOException;
import org.to2mbn.authlibinjector.YggdrasilConfiguration;
import org.to2mbn.authlibinjector.transform.DeprecatedApiTransformUnit;
import org.to2mbn.authlibinjector.transform.TransformUnit;

public class DeprecatedApiHandle {

	public static TransformUnit createTransformUnit(YggdrasilConfiguration configuration) {
		DeprecatedApiHandle handle = new DeprecatedApiHandle(configuration);
		return new DeprecatedApiTransformUnit(() -> {
			handle.ensureStarted();
			return "http://127.0.0.1:" + handle.getLocalApiPort();
		});
	}

	private boolean started = false;
	private YggdrasilConfiguration configuration;
	private DeprecatedApiHttpd httpd;

	private final Object _lock = new Object();

	public DeprecatedApiHandle(YggdrasilConfiguration configuration) {
		this.configuration = configuration;
	}

	public void ensureStarted() {
		if (started)
			return;
		synchronized (_lock) {
			if (started)
				return;
			if (configuration == null)
				throw new IllegalStateException("configuration hasn't been set yet");
			httpd = new DeprecatedApiHttpd(0, configuration);
			try {
				httpd.start();
			} catch (IOException e) {
				throw new IllegalStateException("httpd failed to start");
			}
			info("httpd is running on port {0,number,#}", getLocalApiPort());
			started = true;
		}
	}

	public int getLocalApiPort() {
		if (httpd == null)
			return -1;
		return httpd.getListeningPort();
	}

}
