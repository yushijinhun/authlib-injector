# Dynamic public-key fetching

This feature lets an authentication server publish a reviewed set of public keys for texture-property and player-certificate verification. Servers keep their private keys; clients fetch public keys only from the API root selected for their session. This is explicit multi-root trust, not a central signer: each trusted key has global authority for its purpose, so operators must verify peer-key fingerprints and review them before publishing.

## Enable and publish keys

Set the Boolean metadata flag:

```json
{
  "meta": { "feature.enable_public_key_fetching": true },
  "signaturePublickey": "-----BEGIN PUBLIC KEY-----\n...legacy key...\n-----END PUBLIC KEY-----"
}
```

The agent requests `GET {API root}/minecraftservices/publickeys` with `Accept: application/json`. This public-key request does not carry a player access token or authorization header. Return HTTP 200 and both purpose-specific arrays:

```json
{
  "profilePropertyKeys": [
    { "publicKey": "<Base64 X.509 SubjectPublicKeyInfo DER>" }
  ],
  "playerCertificateKeys": [
    { "publicKey": "<Base64 X.509 SubjectPublicKeyInfo DER>" }
  ]
}
```

The arrays are required but may be empty. Keys use standard Base64 without PEM markers; duplicate keys are removed. Each purpose accepts at most 64 RSA keys, each 2048–8192 bits, and the response body is limited to 128 KiB. The bundled Mojang key remains trusted in both purposes.

Use HTTPS outside loopback. HTTP is allowed only for `localhost`, `127.0.0.1`, or `::1`; redirects are rejected. Connect and read timeouts are 10 seconds. Do not publish keys scraped from arbitrary player profiles or accept a URL supplied by a player.

## Trust and refresh behavior

- If the flag is absent or `false`, no public-key endpoint is fetched. Existing `signaturePublickey` behavior remains in effect along with the bundled Mojang key.
- With the flag enabled, the endpoint is the source of custom keys after a successful fetch. A complete successful snapshot replaces the previous custom-key set, so a removed key is no longer trusted after refresh. The legacy metadata key is not merged into a successful snapshot.
- Before the first successful fetch, or when a snapshot reaches its maximum age, the fallback set contains the metadata `signaturePublickey` (if present) and the bundled Mojang key. The metadata key is therefore trusted for both purposes during fallback. Treat it as a security-sensitive root and remove it from metadata once it is no longer an appropriate fallback.
- A failed refresh does not invalidate a still-fresh successful snapshot and does not extend its age. Once that snapshot expires, only the fallback set is usable. Unknown or invalid signatures are rejected; network failure never counts as a successful verification.
- Fetching starts lazily at the first supported verification or local `/publickeys` request. An in-flight request is shared, periodic refresh continues after activation, and verification may wait up to `waitMillis` for a fetch before retrying once. The default wait is 2000 ms; set it to zero for non-waiting calls, which may reject a signature until keys are available.
- Texture-property and player-certificate keys remain separate. The feature does not scope a key to a player, account namespace, or texture domain. `skinDomains` must still explicitly allow the texture host.

The defaults are a 300-second refresh interval, a 900-second maximum age, a 30-second request cooldown, and a 2000-millisecond caller wait. Override them with:

```text
-Dauthlibinjector.publicKeys.refreshSeconds=300
-Dauthlibinjector.publicKeys.maxAgeSeconds=900
-Dauthlibinjector.publicKeys.cooldownSeconds=30
-Dauthlibinjector.publicKeys.waitMillis=2000
```

The first three values must be integer seconds and satisfy `0 < cooldown <= refresh <= maxAge <= 86400`; `waitMillis` must be from 0 through 10000. The cache is in memory and uses a monotonic clock. Refresh is polling, not immediate revocation: it does not clear every game's skin cache, repaint existing textures, or revoke established sessions.

## Compatibility

The client must run an agent version that implements the feature; changing only the authentication server cannot add multi-key support to an older client. The launcher does not need source changes if it supports selecting or replacing the agent JAR.

Player certificates are verified cryptographically by the supported authlib, Velocity, and BungeeCord integrations. When profile-key support is disabled, the local `/player/certificates` endpoint returns HTTP 204 rather than manufacturing a dummy signature. Deployments that relied on the old always-accept certificate behavior must provide genuinely signed certificates and test their actual game and proxy versions.

## Verification

Run `./gradlew test shadowJar`. The tests cover key parsing, purpose isolation, invalid signatures, endpoint validation and response limits, request refresh and expiry behavior, and packaged-agent verification through the supported integrations. These fixtures do not replace testing with the actual Minecraft client and proxy releases used in deployment.

## 中文摘要

认证站可通过 `meta.feature.enable_public_key_fetching=true` 启用公开公钥接口。客户端只从当前会话选定的 API 根请求 `/minecraftservices/publickeys`，请求不带玩家 access token。`profilePropertyKeys` 与 `playerCertificateKeys` 分用途管理；服务端只发布经过审核的公钥，不交换私钥。

接口成功刷新后，整份自定义密钥快照以接口返回值为准；被移除的密钥不再保留。首次获取失败或缓存到期时，降级信任集为旧 metadata `signaturePublickey`（如果存在）与内置 Mojang 公钥。metadata 密钥在降级期对两个用途都有效，因此应将其视为安全敏感的信任根。失败的下载不会放行未知签名，也不会延长旧快照的有效期。

加载在首次验签或本地 `/publickeys` 请求时按需启动，之后按间隔刷新。默认刷新间隔 300 秒、最大缓存年龄 900 秒、请求冷却 30 秒、调用方等待 2000 毫秒；具体边界见上方 JVM 参数说明。多公钥不会替代 `skinDomains` 白名单，也不会自动让旧客户端获得该能力或清除游戏皮肤缓存。
