package org.to2mbn.authlibinjector;

import static java.util.Optional.ofNullable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.to2mbn.authlibinjector.internal.org.json.JSONObject;
import org.to2mbn.authlibinjector.transform.SkinWhitelistTransformUnit;
import org.to2mbn.authlibinjector.transform.TransformUnit;
import org.to2mbn.authlibinjector.transform.YggdrasilApiTransformUnit;
import org.to2mbn.authlibinjector.transform.YggdrasilKeyTransformUnit;

public class InjectorConfig {

	private static byte[] decodePublicKey(String input) {
		final String header = "-----BEGIN PUBLIC KEY-----\n";
		final String end = "-----END PUBLIC KEY-----";
		if (input.startsWith(header) && input.endsWith(end)) {
			return Base64.getDecoder()
					.decode(input.substring(header.length(), input.length() - end.length()).replace("\n", ""));
		} else {
			throw new IllegalArgumentException("Bad key format");
		}
	}

	private String apiRoot;
	private List<String> skinWhitelistDomains;
	private String publicKey;
	private boolean debug;

	public String getApiRoot() {
		return apiRoot;
	}

	public void setApiRoot(String apiRoot) {
		this.apiRoot = apiRoot;
	}

	public List<String> getSkinWhitelistDomains() {
		return skinWhitelistDomains;
	}

	public void setSkinWhitelistDomains(List<String> skinWhitelistDomains) {
		this.skinWhitelistDomains = skinWhitelistDomains;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public void applyTransformers(List<TransformUnit> units) {
		units.add(new YggdrasilApiTransformUnit(apiRoot));
		units.add(new SkinWhitelistTransformUnit(skinWhitelistDomains.toArray(new String[0])));
		if (publicKey != null) {
			units.add(new YggdrasilKeyTransformUnit(decodePublicKey(publicKey)));
		}
	}

	public void readFromJson(JSONObject json) {
		skinWhitelistDomains = new ArrayList<>();
		ofNullable(json.optJSONArray("skinDomains"))
				.ifPresent(it -> it.forEach(domain -> {
					if (domain instanceof String)
						skinWhitelistDomains.add((String) domain);
				}));
		ofNullable(json.optString("signaturePublickey"))
				.ifPresent(it -> publicKey = it);
	}
}
