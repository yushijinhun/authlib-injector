package moe.yushi.authlibinjector;

public class InjectorInitializationException extends RuntimeException {

	public InjectorInitializationException() {
	}

	public InjectorInitializationException(String message, Throwable cause) {
		super(message, cause);
	}

	public InjectorInitializationException(String message) {
		super(message);
	}

	public InjectorInitializationException(Throwable cause) {
		super(cause);
	}
}
