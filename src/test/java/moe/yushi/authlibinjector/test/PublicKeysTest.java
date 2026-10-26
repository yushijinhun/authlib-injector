/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.test;

import static moe.yushi.authlibinjector.test.UnionTestKeys.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.UncheckedIOException;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.yggdrasil.PublicKeys;

public class PublicKeysTest {
	@Test
	public void separatesPurposesAndDeduplicates() {
		PublicKeys keys = PublicKeys.parse(response(new PublicKey[] { SITE_A.getPublic(), SITE_B.getPublic(), SITE_A.getPublic() },
				new PublicKey[] { SITE_A.getPublic() }).toJSONString());
		assertEquals(Arrays.asList(SITE_A.getPublic(), SITE_B.getPublic()), keys.getProfilePropertyKeys());
		assertEquals(Arrays.asList(SITE_A.getPublic()), keys.getPlayerCertificateKeys());
		assertThrows(UnsupportedOperationException.class, () -> keys.getProfilePropertyKeys().clear());
		assertThrows(UnsupportedOperationException.class, () -> keys.getPlayerCertificateKeys().clear());
	}

	@Test
	public void rejectsMissingOrMalformedLists() {
		for (String json : Arrays.asList("{}", "null", "[]", "not json", "{\"profilePropertyKeys\":[]}",
				"{\"profilePropertyKeys\":null,\"playerCertificateKeys\":[]}",
				"{\"profilePropertyKeys\":[{}],\"playerCertificateKeys\":[]}",
				"{\"profilePropertyKeys\":[{\"publicKey\":\"!\"}],\"playerCertificateKeys\":[]}",
				"{\"profilePropertyKeys\":[],\"playerCertificateKeys\":[{\"publicKey\":12}]}")) {
			assertThrows(UncheckedIOException.class, () -> PublicKeys.parse(json), json);
		}
	}

	@Test
	public void acceptsExplicitlyEmptyLists() {
		PublicKeys keys = PublicKeys.parse("{\"profilePropertyKeys\":[],\"playerCertificateKeys\":[]}");
		assertTrue(keys.getProfilePropertyKeys().isEmpty());
		assertTrue(keys.getPlayerCertificateKeys().isEmpty());
	}

	@Test
	public void rejectsNonRsaAndWeakKeys() throws Exception {
		PublicKey ec = KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic();
		for (PublicKey key : Arrays.asList(ec, generate(1024).getPublic())) {
			assertThrows(UncheckedIOException.class, () -> PublicKeys.parse(
					response(new PublicKey[] { key }, new PublicKey[0]).toJSONString()));
		}
	}

	@Test
	public void rejectsOversizedLists() {
		PublicKey[] tooMany = new PublicKey[PublicKeys.MAX_KEYS_PER_PURPOSE + 1];
		Arrays.fill(tooMany, SITE_A.getPublic());
		JSONObject json = response(tooMany, new PublicKey[0]);
		assertThrows(UncheckedIOException.class, () -> PublicKeys.parse(json.toJSONString()));
	}

	@Test
	public void mergingKeepsBuiltinKeysWithoutDuplicates() {
		PublicKeys mojang = PublicKeys.mojang();
		PublicKeys merged = mojang.merge(mojang).merge(new PublicKeys(Arrays.asList(SITE_A.getPublic()), Arrays.asList(SITE_B.getPublic())));
		assertEquals(mojang.getProfilePropertyKeys().size() + 1, merged.getProfilePropertyKeys().size());
		assertTrue(merged.getPlayerCertificateKeys().contains(SITE_B.getPublic()));
		assertFalse(merged.getPlayerCertificateKeys().contains(SITE_A.getPublic()));
	}
}
