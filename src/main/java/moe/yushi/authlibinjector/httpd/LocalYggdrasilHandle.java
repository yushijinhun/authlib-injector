package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import moe.yushi.authlibinjector.YggdrasilConfiguration;
import moe.yushi.authlibinjector.util.Logging;

public class LocalYggdrasilHandle {

	private boolean started = false;
	private YggdrasilConfiguration configuration;
	private LocalYggdrasilHttpd httpd;

	private final Object _lock = new Object();

	public LocalYggdrasilHandle(YggdrasilConfiguration configuration) {
		this.configuration = configuration;
	}

	public void ensureStarted() {
		if (started)
			return;
		synchronized (_lock) {
			if (started)
				return;
			if (configuration == null)
				throw new IllegalStateException("Configuration hasn't been set yet");
			httpd = new LocalYggdrasilHttpd(0, configuration);
			try {
				httpd.start();
			} catch (IOException e) {
				throw new IllegalStateException("Httpd failed to start");
			}
			Logging.HTTPD.info("Httpd is running on port " + getLocalApiPort());
			started = true;
		}
	}

	public int getLocalApiPort() {
		if (httpd == null)
			return -1;
		return httpd.getListeningPort();
	}

}
