package moe.yushi.authlibinjector.yggdrasil;

import java.util.UUID;

public interface YggdrasilAPIProvider {
	String queryUUIDsByNames();
	String queryProfile(UUID uuid);
}
