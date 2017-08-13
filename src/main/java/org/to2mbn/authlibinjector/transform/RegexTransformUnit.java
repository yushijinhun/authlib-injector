package org.to2mbn.authlibinjector.transform;

import java.util.Optional;
import java.util.regex.Pattern;

public class RegexTransformUnit extends LdcTransformUnit {

	public RegexTransformUnit(Pattern find, String replace) {
		super(input -> Optional.of(find.matcher(input).replaceAll(replace)));
	}

}
