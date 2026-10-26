/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static moe.yushi.authlibinjector.test.UnionTestKeys.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.yggdrasil.PublicKeyLoader;
import moe.yushi.authlibinjector.yggdrasil.PublicKeys;

public class PublicKeyLoaderTest {
	private HttpServer server;
	private final AtomicInteger requests = new AtomicInteger();

	@AfterEach
	public void stopServer() {
		if (server != null) server.stop(0);
	}

	private String serve(int status, String body, boolean chunked) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/nested/api/minecraftservices/publickeys", exchange -> {
			requests.incrementAndGet();
			assertEquals("GET", exchange.getRequestMethod());
			assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] data = body.getBytes(UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.getResponseHeaders().set("Location", "/should-not-follow");
			exchange.sendResponseHeaders(status, chunked ? 0 : data.length);
			try {
				exchange.getResponseBody().write(data);
			} finally {
				exchange.close();
			}
		});
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/nested/api";
	}

	private APIMetadata metadata(String root, Object flag) {
		return new APIMetadata(root, Collections.emptyList(), Collections.singletonMap(PublicKeyLoader.FEATURE, flag),
				Optional.of(UNKNOWN.getPublic()));
	}

	@Test
	public void legacyModeDoesNotFetchAndTrustsLegacyKeyForBothPurposes() throws Exception {
		String root = serve(500, "failure", false);
		for (Object flag : Arrays.asList(null, false)) {
			PublicKeys keys = PublicKeyLoader.load(metadata(root, flag));
			assertTrue(keys.getProfilePropertyKeys().contains(UNKNOWN.getPublic()));
			assertTrue(keys.getPlayerCertificateKeys().contains(UNKNOWN.getPublic()));
		}
		assertEquals(0, requests.get());
	}

	@Test
	public void loadsNestedApiRootWithOrWithoutTrailingSlashAndKeepsPurposesSeparate() throws Exception {
		String root = serve(200, response(new PublicKey[] { SITE_A.getPublic(), SITE_B.getPublic() },
				new PublicKey[] { SITE_A.getPublic() }).toJSONString(), false);
		for (String url : Arrays.asList(root, root + "/")) {
			PublicKeys keys = PublicKeyLoader.load(metadata(url, true));
			assertTrue(keys.getProfilePropertyKeys().contains(SITE_B.getPublic()));
			assertTrue(keys.getPlayerCertificateKeys().contains(SITE_A.getPublic()));
			assertFalse(keys.getPlayerCertificateKeys().contains(SITE_B.getPublic()));
			assertFalse(keys.getProfilePropertyKeys().contains(UNKNOWN.getPublic()));
			assertTrue(keys.getProfilePropertyKeys().containsAll(PublicKeys.mojang().getProfilePropertyKeys()));
		}
		assertEquals(2, requests.get());
	}

	@Test
	public void failsClosedOnServerError() throws Exception {
		String root = serve(503, "unavailable", false);
		assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata(root, true)));
	}

	@Test
	public void rejectsRedirects() throws Exception {
		String root = serve(302, "redirect", false);
		assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata(root, true)));
		assertEquals(1, requests.get());
	}

	@Test
	public void failsClosedOnMalformedResponse() throws Exception {
		String root = serve(200, "{}", false);
		assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata(root, true)));
	}

	@Test
	public void rejectsOversizedContentLength() throws Exception {
		String root = serve(200, String.join("", Collections.nCopies(PublicKeyLoader.MAX_RESPONSE_BYTES + 1, " ")), false);
		assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata(root, true)));
	}

	@Test
	public void boundsChunkedResponsesToo() throws Exception {
		String root = serve(200, String.join("", Collections.nCopies(PublicKeyLoader.MAX_RESPONSE_BYTES + 1, " ")), true);
		assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata(root, true)));
	}

	@Test
	public void rejectsUnsafeRootsBeforeNetworking() {
		for (String root : Arrays.asList("http://example.com/api", "file:///tmp/keys", "https://example.com/api?root=x",
				"https://user:password@example.com/api", "https://example.com/api#fragment", "not a URI")) {
			assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata(root, true)), root);
		}
	}

	@Test
	public void rejectsNonBooleanOptIn() {
		assertThrows(IOException.class, () -> PublicKeyLoader.load(metadata("https://example.com", "true")));
	}
}
