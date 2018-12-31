package moe.yushi.authlibinjector.internal.fi.iki.elonen;

public class ResponseException extends Exception {

	private final Status status;

	public ResponseException(Status status, String message) {
		super(message);
		this.status = status;
	}

	public ResponseException(Status status, String message, Exception e) {
		super(message, e);
		this.status = status;
	}

	public Status getStatus() {
		return this.status;
	}
}
