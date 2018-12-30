package moe.yushi.authlibinjector.httpd;

import static moe.yushi.authlibinjector.util.IOUtils.CONTENT_TYPE_JSON;
import static moe.yushi.authlibinjector.util.IOUtils.asBytes;
import static moe.yushi.authlibinjector.util.IOUtils.asString;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonArray;
import static moe.yushi.authlibinjector.util.JsonUtils.asJsonString;
import static moe.yushi.authlibinjector.util.JsonUtils.parseJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilResponseBuilder;

public class QueryUUIDsFilter implements URLFilter {

	private YggdrasilClient mojangClient;
	private YggdrasilClient customClient;

	public QueryUUIDsFilter(YggdrasilClient mojangClient, YggdrasilClient customClient) {
		this.mojangClient = mojangClient;
		this.customClient = customClient;
	}

	@Override
	public boolean canHandle(String domain, String path) {
		return domain.equals("api.mojang.com") && path.startsWith("/profiles/");
	}

	@Override
	public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
		if (domain.equals("api.mojang.com") && path.equals("/profiles/minecraft") && session.getMethod().equals("POST")) {
			Set<String> request = new LinkedHashSet<>();
			asJsonArray(parseJson(asString(asBytes(session.getInputStream()))))
					.forEach(element -> request.add(asJsonString(element)));
			return Optional.of(Response.newFixedLength(Status.OK, CONTENT_TYPE_JSON,
					YggdrasilResponseBuilder.queryUUIDs(performQuery(request))));
		} else {
			return Optional.empty();
		}
	}

	private Map<String, UUID> performQuery(Set<String> names) {
		Set<String> customNames = new LinkedHashSet<>();
		Set<String> mojangNames = new LinkedHashSet<>();
		names.forEach(name -> {
			if (name.endsWith(NAME_SUFFIX)) {
				mojangNames.add(name.substring(0, name.length() - NAME_SUFFIX.length()));
			} else {
				customNames.add(name);
			}
		});

		Map<String, UUID> result = new LinkedHashMap<>();
		if (!customNames.isEmpty()) {
			result.putAll(customClient.queryUUIDs(customNames));
		}
		if (!mojangNames.isEmpty()) {
			mojangClient.queryUUIDs(mojangNames)
					.forEach((name, uuid) -> {
						result.put(name + NAME_SUFFIX, maskUUID(uuid));
					});
		}
		return result;
	}

	private static final int MSB_MASK = 0x00008000;
	static final String NAME_SUFFIX = "@mojang";

	static UUID maskUUID(UUID uuid) {
		if (isMaskedUUID(uuid)) {
			Logging.HTTPD.warning("UUID already masked: " + uuid);
		}
		return new UUID(uuid.getMostSignificantBits() | MSB_MASK, uuid.getLeastSignificantBits());
	}

	static boolean isMaskedUUID(UUID uuid) {
		return (uuid.getMostSignificantBits() & MSB_MASK) != 0;
	}

	static UUID unmaskUUID(UUID uuid) {
		return new UUID(uuid.getMostSignificantBits() & (~MSB_MASK), uuid.getLeastSignificantBits());
	}

}
