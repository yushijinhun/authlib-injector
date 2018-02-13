package org.to2mbn.authlibinjector;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.to2mbn.authlibinjector.util.KeyUtils.decodePublicKey;
import static org.to2mbn.authlibinjector.util.KeyUtils.loadX509PublicKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.to2mbn.authlibinjector.internal.org.json.JSONException;
import org.to2mbn.authlibinjector.internal.org.json.JSONObject;

public class YggdrasilConfiguration {

	public static YggdrasilConfiguration parse(String apiRoot, String metadataResponse) throws IOException {
		if (!apiRoot.endsWith("/")) apiRoot += "/";

		try {
			JSONObject response = new JSONObject(metadataResponse);

			List<String> skinDomains = new ArrayList<>();
			ofNullable(response.optJSONArray("skinDomains"))
					.ifPresent(it -> it.forEach(domain -> {
						if (domain instanceof String)
							skinDomains.add((String) domain);
					}));

			Optional<String> signaturePublickey = ofNullable(response.optString("signaturePublickey"));
			Optional<PublicKey> decodedPublickey;

			if (signaturePublickey.isPresent()) {
				try {
					decodedPublickey = of(loadX509PublicKey(decodePublicKey(signaturePublickey.get())));
				} catch (IllegalArgumentException | GeneralSecurityException e) {
					throw new IOException("Bad signature publickey", e);
				}
			} else {
				decodedPublickey = empty();
			}

			Map<String, String> meta = new TreeMap<>();
			ofNullable(response.optJSONObject("meta"))
					.map(JSONObject::toMap)
					.ifPresent(it -> it.forEach((k, v) -> meta.put(k, String.valueOf(v))));

			return new YggdrasilConfiguration(apiRoot, unmodifiableList(skinDomains), signaturePublickey, unmodifiableMap(meta), decodedPublickey);
		} catch (JSONException e) {
			throw new IOException("Invalid json", e);
		}
	}

	private String apiRoot;
	private List<String> skinDomains;
	private Optional<String> signaturePublickey;
	private Optional<PublicKey> decodedPublickey;
	private Map<String, String> meta;

	public YggdrasilConfiguration(String apiRoot, List<String> skinDomains, Optional<String> signaturePublickey, Map<String, String> meta, Optional<PublicKey> decodedPublickey) {
		this.apiRoot = requireNonNull(apiRoot);
		this.skinDomains = requireNonNull(skinDomains);
		this.signaturePublickey = requireNonNull(signaturePublickey);
		this.meta = requireNonNull(meta);
		this.decodedPublickey = requireNonNull(decodedPublickey);
	}

	public String getApiRoot() {
		return apiRoot;
	}

	public List<String> getSkinDomains() {
		return skinDomains;
	}

	public Optional<String> getSignaturePublickey() {
		return signaturePublickey;
	}

	public Map<String, String> getMeta() {
		return meta;
	}

	public Optional<PublicKey> getDecodedPublickey() {
		return decodedPublickey;
	}

	@Override
	public String toString() {
		return "YggdrasilConfiguration [apiRoot=" + apiRoot + ", skinDomains=" + skinDomains + ", signaturePublickey=" + signaturePublickey + ", meta=" + meta + "]";
	}
}
