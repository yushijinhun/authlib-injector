/* Copyright (C) 2026 authlib-injector contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package moe.yushi.authlibinjector.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static moe.yushi.authlibinjector.test.UnionTestKeys.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.yggdrasil.PublicKeyLoader;
import union.smoke.AgentSmokeMain;
import union.smoke.AgentLifecycleMain;

public class PackagedAgentTest {
	@TempDir public Path directory;

	@Test
	public void packagedAgentVerifiesBothStationsAndRejectsInvalidSignatures() throws Exception {
		runAgent(true, false, false, false);
	}

	@Test
	public void launcherPrefetchStillFetchesPublicKeys() throws Exception {
		runAgent(true, true, false, false);
	}

	@Test
	public void legacyMetadataNeedsNoPublicKeyEndpoint() throws Exception {
		runAgent(false, false, false, false);
	}

	@Test
	public void badPublicKeyEndpointAllowsStartupAndUsesMetadataFallback() throws Exception {
		runAgent(true, true, true, false, "unavailable");
	}

	@Test
	public void incompatibleHttpdFlagStopsBeforeMain() throws Exception {
		runAgent(true, true, false, true);
	}

	@Test
	public void unusedAgentNeverFetchesPublicKeys() throws Exception {
		runAgent(true, true, false, false, "unused");
	}

	@Test
	public void nativePublickeysRequestCanBeFirstUse() throws Exception {
		runAgent(true, true, false, false, "http-first");
	}

	@Test
	public void runningAgentAppliesPeriodicRevocationToExistingVerifiers() throws Exception {
		runAgent(true, true, false, false, "rotation");
	}

	@Test
	public void signatureMissFetchesNewKeyAndRetriesOriginalProperty() throws Exception {
		runAgent(true, true, false, false, "miss");
	}

	@Test
	public void outageCannotKeepOldCustomKeysAlivePastMaximumAge() throws Exception {
		runAgent(true, true, false, false, "expiry");
	}

	private void runAgent(boolean union, boolean prefetch, boolean badEndpoint, boolean disableHttpd) throws Exception {
		runAgent(union, prefetch, badEndpoint, disableHttpd, "default");
	}

	private void runAgent(boolean union, boolean prefetch, boolean badEndpoint, boolean disableHttpd, String mode) throws Exception {
		JSONObject meta = new JSONObject();
		meta.put(PublicKeyLoader.FEATURE, union);
		JSONObject metadata = new JSONObject();
		metadata.put("meta", meta);
		// With opt-in, the endpoint must override this unrelated legacy key.
		metadata.put("signaturePublickey", pem(union ? UNKNOWN.getPublic() : SITE_A.getPublic()));
		byte[] metadataBytes = metadata.toJSONString().getBytes(UTF_8);
		byte[] keyBytes = (badEndpoint ? "{}" : response("miss".equals(mode) ? new PublicKey[] { SITE_A.getPublic() }
				: new PublicKey[] { SITE_A.getPublic(), SITE_B.getPublic() },
				new PublicKey[] { SITE_A.getPublic() }).toJSONString()).getBytes(UTF_8);
		byte[] rotatedBytes = response("rotation".equals(mode) ? new PublicKey[] { SITE_A.getPublic() }
				: new PublicKey[] { SITE_A.getPublic(), SITE_B.getPublic() },
				"rotation".equals(mode) ? new PublicKey[] { SITE_B.getPublic() } : new PublicKey[] { SITE_A.getPublic() }).toJSONString().getBytes(UTF_8);
		AtomicInteger metadataRequests = new AtomicInteger(), keyRequests = new AtomicInteger();
		AtomicInteger stage = new AtomicInteger();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/auth/", exchange -> {
			if (exchange.getRequestURI().getPath().equals("/auth/_test/rotate")) {
				stage.set(1);
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			boolean keyRequest = exchange.getRequestURI().getPath().equals("/auth/minecraftservices/publickeys");
			(keyRequest ? keyRequests : metadataRequests).incrementAndGet();
			byte[] body = keyRequest ? (stage.get() == 0 ? keyBytes : rotatedBytes) : metadataBytes;
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(keyRequest && stage.get() > 0 && "expiry".equals(mode) ? 503 : 200, body.length);
			try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
		});
		server.start();
		Process child = null;
		try {
			String root = "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/";
			String jar = System.getProperty("authlib.agentJar");
			assertNotNull(jar, "Run through Gradle test to build the agent first");
			List<String> command = new ArrayList<>(Arrays.asList(
					new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
					"-Dauthlibinjector.noLogFile", "-javaagent:" + jar + "=" + root));
			if (prefetch) command.add("-Dauthlibinjector.yggdrasil.prefetched=" + encode(metadataBytes));
			if (disableHttpd) command.add("-Dauthlibinjector.disableHttpd");
			if (Arrays.asList("rotation", "miss", "expiry").contains(mode)) {
				command.add("-Dauthlibinjector.publicKeys.cooldownSeconds=1");
				command.add("-Dauthlibinjector.publicKeys.refreshSeconds=" + ("miss".equals(mode) ? "60" : "1"));
				command.add("-Dauthlibinjector.publicKeys.maxAgeSeconds=" + ("miss".equals(mode) ? "120" : "3"));
			}
			command.addAll(Arrays.asList("-cp", jar + File.pathSeparator + System.getProperty("authlib.testClasses"),
					"default".equals(mode) ? AgentSmokeMain.class.getName() : AgentLifecycleMain.class.getName(), Boolean.toString(union)));
			for (java.security.KeyPair site : Arrays.asList(SITE_A, SITE_B, UNKNOWN)) {
				command.add(encode(sign(site.getPrivate(), AgentSmokeMain.VALUE.getBytes(UTF_8))));
			}
			command.add(encode(SITE_B.getPublic().getEncoded()));
			byte[] der = UNKNOWN.getPublic().getEncoded();
			command.add(encode(der));
			long expiry = 2000000000000L;
			String pem = "-----BEGIN RSA PUBLIC KEY-----\n"
					+ Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(der) + "\n-----END RSA PUBLIC KEY-----\n";
			command.add(encode(sign(SITE_A.getPrivate(), (expiry + pem).getBytes(UTF_8))));
			UUID holder = UUID.randomUUID();
			byte[] v2 = ByteBuffer.allocate(24 + der.length).putLong(holder.getMostSignificantBits())
					.putLong(holder.getLeastSignificantBits()).putLong(expiry).put(der).array();
			command.add(encode(sign(SITE_A.getPrivate(), v2)));
			command.add(encode(sign(SITE_B.getPrivate(), v2)));
			command.add(holder.toString());
			command.add(Long.toString(expiry));
			command.add(mode);
			command.add(root);
			Path log = directory.resolve("child.log");
			child = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true)
					.redirectOutput(log.toFile()).start();
			assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Agent child JVM timed out");
			String output = new String(Files.readAllBytes(log), UTF_8);
			if (disableHttpd) {
				assertNotEquals(0, child.exitValue(), output);
				assertFalse(output.contains("UNION_AGENT_SMOKE_OK"), output);
				assertTrue(output.contains("Failed to load trusted public keys"), output);
			} else {
				assertEquals(0, child.exitValue(), output);
				assertTrue(output.contains("UNION_AGENT_SMOKE_OK"), output);
			}
			assertEquals(prefetch ? 0 : 1, metadataRequests.get());
			if (Arrays.asList("rotation", "miss", "expiry").contains(mode)) {
				assertTrue(keyRequests.get() >= 2, output);
			} else {
				assertEquals(union && !disableHttpd && !"unused".equals(mode) ? 1 : 0, keyRequests.get(), output);
			}
		} finally {
			if (child != null && child.isAlive()) {
				child.destroyForcibly();
				child.waitFor(5, TimeUnit.SECONDS);
			}
			server.stop(0);
		}
	}

	private static String encode(byte[] value) { return Base64.getEncoder().encodeToString(value); }
}
