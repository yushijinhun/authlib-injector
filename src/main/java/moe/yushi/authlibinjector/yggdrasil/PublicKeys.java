/*
 * Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package moe.yushi.authlibinjector.yggdrasil;

import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.IOUtils.newUncheckedIOException;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonObject;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonString;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.KeyUtils;

/** Immutable, purpose-separated trust snapshot. No private keys are exchanged. */
public final class PublicKeys {

	public static final int MAX_KEYS_PER_PURPOSE = 64;
	private final List<PublicKey> profilePropertyKeys;
	private final List<PublicKey> playerCertificateKeys;

	public PublicKeys(Collection<PublicKey> profilePropertyKeys, Collection<PublicKey> playerCertificateKeys) {
		this.profilePropertyKeys = immutableKeys(profilePropertyKeys);
		this.playerCertificateKeys = immutableKeys(playerCertificateKeys);
	}

	private static List<PublicKey> immutableKeys(Collection<PublicKey> keys) {
		if (keys.contains(null)) {
			throw new IllegalArgumentException("Null public key");
		}
		return Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(keys)));
	}

	public List<PublicKey> getProfilePropertyKeys() {
		return profilePropertyKeys;
	}

	public List<PublicKey> getPlayerCertificateKeys() {
		return playerCertificateKeys;
	}

	public PublicKeys merge(PublicKeys other) {
		List<PublicKey> properties = new ArrayList<>(profilePropertyKeys);
		properties.addAll(other.profilePropertyKeys);
		List<PublicKey> certificates = new ArrayList<>(playerCertificateKeys);
		certificates.addAll(other.playerCertificateKeys);
		return new PublicKeys(properties, certificates);
	}

	public static PublicKeys parse(String json) {
		JSONObject response = asJsonObject(parseJson(json));
		return new PublicKeys(parseKeys(response, "profilePropertyKeys"), parseKeys(response, "playerCertificateKeys"));
	}

	private static List<PublicKey> parseKeys(JSONObject response, String purpose) {
		JSONArray entries = asJsonArray(response.get(purpose));
		if (entries.size() > MAX_KEYS_PER_PURPOSE) {
			throw newUncheckedIOException("Too many " + purpose);
		}
		List<PublicKey> keys = new ArrayList<>();
		for (Object entry : entries) {
			String encoded = asJsonString(asJsonObject(entry).get("publicKey"));
			if (encoded.length() > 8192) {
				throw newUncheckedIOException("Oversized public key in " + purpose);
			}
			try {
				RSAPublicKey key = (RSAPublicKey) KeyUtils.parseX509PublicKey(Base64.getDecoder().decode(encoded));
				int bits = key.getModulus().bitLength();
				if (bits < 2048 || bits > 8192) {
					throw new GeneralSecurityException("RSA key must be between 2048 and 8192 bits");
				}
				keys.add(key);
			} catch (IllegalArgumentException | GeneralSecurityException e) {
				throw newUncheckedIOException("Invalid RSA public key in " + purpose, e);
			}
		}
		return keys;
	}

	public static PublicKeys mojang() {
		try (InputStream in = PublicKeys.class.getResourceAsStream("/mojang_publickey.der")) {
			if (in == null) {
				throw new IOException("Missing bundled Mojang public key");
			}
			List<PublicKey> keys = Collections.singletonList(KeyUtils.parseX509PublicKey(asBytes(in)));
			return new PublicKeys(keys, keys);
		} catch (IOException | GeneralSecurityException e) {
			throw new IllegalStateException("Failed to load Mojang public key", e);
		}
	}
}
