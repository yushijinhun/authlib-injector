package moe.yushi.authlibinjector.transform;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class DomainBasedTransformUnit extends LdcTransformUnit {

	private static final Pattern URL_REGEX = Pattern.compile("^https?:\\/\\/(?<domain>[^\\/]+)(?<path>\\/.*)$");

	private Map<String, String> domainMapping = new ConcurrentHashMap<>();

	public Map<String, String> getDomainMapping() {
		return domainMapping;
	}

	@Override
	public Optional<String> transformLdc(String input) {
		Matcher matcher = URL_REGEX.matcher(input);
		if (!matcher.find()) {
			return Optional.empty();
		}

		String domain = matcher.group("domain");
		String subdirectory = domainMapping.get(domain);
		if (subdirectory == null) {
			return Optional.empty();
		}

		String path = matcher.group("path");

		return Optional.of(getApiRoot() + subdirectory + path);
	}

	protected abstract String getApiRoot();
}
