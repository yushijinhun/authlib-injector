package moe.yushi.authlibinjector.transform;

import java.util.Optional;

import moe.yushi.authlibinjector.httpd.URLProcessor;

public class ConstantURLTransformUnit extends LdcTransformUnit {

	private URLProcessor urlProcessor;

	public ConstantURLTransformUnit(URLProcessor urlProcessor) {
		this.urlProcessor = urlProcessor;
	}

	@Override
	protected Optional<String> transformLdc(String input) {
		return urlProcessor.transformURL(input);
	}

	@Override
	public String toString() {
		return "Constant URL Transformer";
	}
}
