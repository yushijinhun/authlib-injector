/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static moe.yushi.authlibinjector.test.UnionTestKeys.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import moe.yushi.authlibinjector.httpd.PublickeysFilter;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.transform.support.BungeeCordProfileKeyTransformUnit;
import moe.yushi.authlibinjector.transform.support.VelocityProfileKeyTransformUnit;
import moe.yushi.authlibinjector.transform.support.YggdrasilKeyTransformUnit;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.MultiKeySignature;
import moe.yushi.authlibinjector.yggdrasil.PublicKeys;

public class UnionSignatureTest {
	private static final String VALUE = "eyJ0ZXh0dXJlcyI6e319";
	private PublicKeys previous;

	@BeforeEach
	public void configure() {
		previous = YggdrasilKeyTransformUnit.getPublicKeys();
		YggdrasilKeyTransformUnit.setPublicKeys(new PublicKeys(Arrays.asList(SITE_A.getPublic(), SITE_B.getPublic()),
				Collections.singletonList(SITE_A.getPublic())));
	}

	@AfterEach
	public void restore() {
		YggdrasilKeyTransformUnit.setPublicKeys(previous);
	}

	@Test
	public void acceptsBothStationsForLegacyAndRecordStyleProperties() throws Exception {
		for (java.security.KeyPair site : Arrays.asList(SITE_A, SITE_B)) {
			String signature = Base64.getEncoder().encodeToString(sign(site.getPrivate(), VALUE.getBytes(UTF_8)));
			assertTrue(YggdrasilKeyTransformUnit.verifyPropertySignature(new LegacyProperty(VALUE, signature)));
			assertTrue(YggdrasilKeyTransformUnit.verifyPropertySignature(new ModernProperty(VALUE, signature)));
		}
	}

	@Test
	public void rejectsUnknownTamperedMissingAndMalformedPropertySignatures() throws Exception {
		String valid = Base64.getEncoder().encodeToString(sign(SITE_A.getPrivate(), VALUE.getBytes(UTF_8)));
		String unknown = Base64.getEncoder().encodeToString(sign(UNKNOWN.getPrivate(), VALUE.getBytes(UTF_8)));
		assertFalse(YggdrasilKeyTransformUnit.verifyPropertySignature(new LegacyProperty(VALUE, unknown)));
		assertFalse(YggdrasilKeyTransformUnit.verifyPropertySignature(new LegacyProperty(VALUE + "x", valid)));
		for (String signature : Arrays.asList(null, "", "!", "AA==")) {
			assertFalse(YggdrasilKeyTransformUnit.verifyPropertySignature(new LegacyProperty(VALUE, signature)));
		}
		assertFalse(YggdrasilKeyTransformUnit.verifyPropertySignature(new LegacyProperty(null, valid)));
	}

	@Test
	public void certificateVerifierDoesNotTrustTextureOnlyKey() throws Exception {
		byte[] payload = "certificate bytes".getBytes(UTF_8);
		Signature verifier = YggdrasilKeyTransformUnit.createCertificateSignature();
		verifier.update(payload);
		assertTrue(verifier.verify(sign(SITE_A.getPrivate(), payload)));
		verifier.update(payload);
		assertFalse(verifier.verify(sign(SITE_B.getPrivate(), payload)));
		verifier.update(payload);
		assertFalse(verifier.verify(new byte[] { 0 }));
		verifier.update(payload);
		assertTrue(verifier.verify(sign(SITE_A.getPrivate(), payload)), "Verifier must reset after failures");
	}

	@Test
	public void certificateOnlyKeyCannotSignTextureProperties() throws Exception {
		YggdrasilKeyTransformUnit.setPublicKeys(new PublicKeys(Collections.singletonList(SITE_A.getPublic()),
				Arrays.asList(SITE_A.getPublic(), SITE_B.getPublic())));
		byte[] payload = VALUE.getBytes(UTF_8);
		byte[] signed = sign(SITE_B.getPrivate(), payload);
		assertFalse(YggdrasilKeyTransformUnit.verifyPropertySignature(
				new ModernProperty(VALUE, Base64.getEncoder().encodeToString(signed))));
		Signature verifier = YggdrasilKeyTransformUnit.createCertificateSignature();
		verifier.update(payload);
		assertTrue(verifier.verify(signed));
		verifier.update((VALUE + "tampered").getBytes(UTF_8));
		assertFalse(verifier.verify(signed));
	}

	@Test
	public void handlesDifferentRsaKeyLengthsAndStreamingUpdates() throws Exception {
		Signature verifier = new MultiKeySignature(Arrays.asList(PublicKeys.mojang().getPlayerCertificateKeys().get(0), SITE_B.getPublic()));
		byte[] payload = "payload".getBytes(UTF_8);
		verifier.update(payload[0]);
		verifier.update(payload, 1, payload.length - 1);
		assertTrue(verifier.verify(sign(SITE_B.getPrivate(), payload)));
	}

	@Test
	public void emptyTrustSetCannotVerifyAnything() throws Exception {
		Signature verifier = new MultiKeySignature(Collections.emptyList());
		verifier.update(VALUE.getBytes(UTF_8));
		assertFalse(verifier.verify(sign(SITE_A.getPrivate(), VALUE.getBytes(UTF_8))));
	}

	@Test
	public void certificateBufferIsBoundedAndCannotAcceptTruncatedPayload() throws Exception {
		Signature verifier = new MultiKeySignature(Collections.singletonList(SITE_A.getPublic()));
		verifier.update(new byte[MultiKeySignature.MAX_PAYLOAD_BYTES]);
		assertThrows(java.security.SignatureException.class, () -> verifier.update((byte) 0));
		assertFalse(verifier.verify(sign(SITE_A.getPrivate(), new byte[0])));
		verifier.update(VALUE.getBytes(UTF_8));
		assertTrue(verifier.verify(sign(SITE_A.getPrivate(), VALUE.getBytes(UTF_8))));
		assertThrows(java.security.SignatureException.class, () -> verifier.update(new byte[MultiKeySignature.MAX_PAYLOAD_BYTES + 1]));
		assertFalse(verifier.verify(sign(SITE_A.getPrivate(), new byte[0])));
	}

	@Test
	public void publickeysEndpointPreservesPurposeSeparation() throws Exception {
		IHTTPSession session = (IHTTPSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IHTTPSession.class },
				(proxy, method, args) -> "getMethod".equals(method.getName()) ? "GET" : null);
		try (Response response = new PublickeysFilter().handle(null, "api.minecraftservices.com", "/publickeys", session).get()) {
			PublicKeys keys = PublicKeys.parse(IOUtils.asString(IOUtils.asBytes(response.getData())));
			assertEquals(Arrays.asList(SITE_A.getPublic(), SITE_B.getPublic()), keys.getProfilePropertyKeys());
			assertEquals(Collections.singletonList(SITE_A.getPublic()), keys.getPlayerCertificateKeys());
		}
	}

	@Test
	public void proxyCallbacksVerifyUuidBoundCertificateAndRejectTampering() throws Exception {
		UUID holder = UUID.randomUUID();
		long expiry = 2000000000000L;
		byte[] der = UNKNOWN.getPublic().getEncoded();
		byte[] payload = ByteBuffer.allocate(24 + der.length).putLong(holder.getMostSignificantBits())
				.putLong(holder.getLeastSignificantBits()).putLong(expiry).put(der).array();
		byte[] signed = sign(SITE_A.getPrivate(), payload);
		BungeeKey key = new BungeeKey(der, expiry, signed);
		assertTrue(BungeeCordProfileKeyTransformUnit.verifyCertificate(key, holder));
		assertFalse(BungeeCordProfileKeyTransformUnit.verifyCertificate(key, UUID.randomUUID()));
		assertFalse(BungeeCordProfileKeyTransformUnit.verifyCertificate(new BungeeKey(der, expiry + 1, signed), holder));
		assertFalse(BungeeCordProfileKeyTransformUnit.verifyCertificate(new BungeeKey(der, expiry, sign(SITE_B.getPrivate(), payload)), holder));
		VelocityKey velocity = new VelocityKey(Revision.LINKED_V2, UNKNOWN.getPublic(), expiry, signed);
		assertEquals(Boolean.TRUE, VelocityProfileKeyTransformUnit.verifyCertificate(velocity, holder));
		assertEquals(Boolean.FALSE, VelocityProfileKeyTransformUnit.verifyCertificate(velocity, UUID.randomUUID()));
		assertNull(VelocityProfileKeyTransformUnit.verifyCertificate(velocity, null));
	}

	@Test
	public void proxyCallbacksVerifyLegacyCertificateFormat() throws Exception {
		long expiry = 2000000000000L;
		byte[] der = UNKNOWN.getPublic().getEncoded();
		String pem = "-----BEGIN RSA PUBLIC KEY-----\n"
				+ Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(der) + "\n-----END RSA PUBLIC KEY-----\n";
		byte[] signed = sign(SITE_A.getPrivate(), (expiry + pem).getBytes(UTF_8));
		assertTrue(BungeeCordProfileKeyTransformUnit.verifyCertificate(new BungeeKey(der, expiry, signed), null));
		assertEquals(Boolean.TRUE, VelocityProfileKeyTransformUnit.verifyCertificate(
				new VelocityKey(Revision.GENERIC_V1, UNKNOWN.getPublic(), expiry, signed), UUID.randomUUID()));
		assertEquals(Boolean.FALSE, VelocityProfileKeyTransformUnit.verifyCertificate(
				new VelocityKey(Revision.UNKNOWN, UNKNOWN.getPublic(), expiry, signed), UUID.randomUUID()));
		assertFalse(BungeeCordProfileKeyTransformUnit.verifyCertificate(new Object(), null));
		assertEquals(Boolean.FALSE, VelocityProfileKeyTransformUnit.verifyCertificate(new Object(), null));
	}

	public static class LegacyProperty {
		private final String value, signature;
		public LegacyProperty(String value, String signature) { this.value = value; this.signature = signature; }
		public String getValue() { return value; }
		public String getSignature() { return signature; }
	}

	public static class ModernProperty {
		private final String value, signature;
		public ModernProperty(String value, String signature) { this.value = value; this.signature = signature; }
		public String value() { return value; }
		public String signature() { return signature; }
	}

	public static class BungeeKey {
		private final byte[] key, signature;
		private final long expiry;
		public BungeeKey(byte[] key, long expiry, byte[] signature) { this.key = key; this.expiry = expiry; this.signature = signature; }
		public byte[] getKey() { return key; }
		public byte[] getSignature() { return signature; }
		public long getExpiry() { return expiry; }
	}

	public enum Revision { GENERIC_V1, LINKED_V2, UNKNOWN }

	public static class VelocityKey {
		private final Revision revision;
		private final PublicKey key;
		private final long expiry;
		private final byte[] signature;
		public VelocityKey(Revision revision, PublicKey key, long expiry, byte[] signature) {
			this.revision = revision; this.key = key; this.expiry = expiry; this.signature = signature;
		}
		public Revision getKeyRevision() { return revision; }
		public PublicKey getSignedPublicKey() { return key; }
		public Instant getExpiryTemporal() { return Instant.ofEpochMilli(expiry); }
		public byte[] getSignature() { return signature; }
	}
}
