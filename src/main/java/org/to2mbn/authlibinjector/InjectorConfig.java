package org.to2mbn.authlibinjector;

import static org.to2mbn.authlibinjector.util.KeyUtils.decodePublicKey;
import java.util.List;
import org.to2mbn.authlibinjector.transform.SkinWhitelistTransformUnit;
import org.to2mbn.authlibinjector.transform.TransformUnit;
import org.to2mbn.authlibinjector.transform.YggdrasilApiTransformUnit;
import org.to2mbn.authlibinjector.transform.YggdrasilKeyTransformUnit;

public class InjectorConfig {

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
}
