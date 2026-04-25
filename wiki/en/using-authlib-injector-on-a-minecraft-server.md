# Using authlib-injector on a Minecraft server

## Obtaining authlib-injector

First, you need to download the latest version of authlib-injector from [here](https://authlib-injector.yushi.moe/).

## Vanilla server/Spigot/Paper/...
> Since authlib-injector v1.2.0, you need to set `enforce-secure-profile` to `true`, which is different from previous versions!
>
> If you encounter issues related to chat message signing on MC 1.19+, please read:
> :point_right: [authlib-injector v1.2.0 Upgrade FAQ #174](https://github.com/yushijinhun/authlib-injector/discussions/174) :point_left:

Please set `online-mode` to `true` in the server's `server.properties`. For 1.19+ servers, you also need to **set `enforce-secure-profile` to `true`**.

Then add the following JVM argument to the server's startup command (the added argument should be placed **before** `-jar`):

```
-javaagent:{path/to/authlib-injector.jar}={https://your-yggdrasil-api-root.com}
```

- `{path/to/authlib-injector.jar}` indicates the location of the JAR file you downloaded in the [previous step](#obtaining-authlib-injector) (either a relative or an absolute path is acceptable).
- `{https://your-yggdrasil-api-root.com}` indicates the URL of the authentication server.

For example, this is the original startup command:

```
java -jar minecraft_server.1.12.2.jar nogui
```

Assuming:

- The authlib-injector JAR file you downloaded is named `authlib-injector.jar`.
- You placed it in the same directory as the server JAR `minecraft_server.1.12.2.jar`.
- The authentication server URL is `https://example.yggdrasil.yushi.moe`.

Then the command line after adding the argument should be as follows:

```
java -javaagent:authlib-injector.jar=https://example.yggdrasil.yushi.moe -jar minecraft_server.1.12.2.jar nogui
```

## BungeeCord/Velocity
If you are using BungeeCord or Velocity, then authlib-injector needs to be loaded on all servers **as well as BungeeCord/Velocity** (see the method above), and `enforce-secure-chat` should be enabled. However, only BungeeCord/Velocity should have `online-mode` enabled; the backend MC servers should have `online-mode` disabled.

## Fetching Mojang Skins
After loading authlib-injector, all skins are fetched from the specified authentication server by default. For example:
* `/give @p minecraft:skull 1 3 {SkullOwner:"notch"}`
* (Citizens2 plugin) `/npc skin notch`

These commands fetch the skin of the character named `notch` from the **custom authentication server**.

If you want to use Mojang skins, you can append `@mojang` to the character name, such as:
* `/give @p minecraft:skull 1 3 {SkullOwner:"notch@mojang"}`
* `/npc skin notch@mojang`

For detailed instructions, see the `-Dauthlibinjector.mojangNamespace` option in [README § Options](../../README.en.md#Options).

### Accessing Mojang via Proxy
The feature to fetch Mojang skins requires the MC server to be able to access the Mojang API. If your server needs to access Mojang through a proxy, you can add the following **JVM argument** at startup to specify the proxy:
```
-Dauthlibinjector.mojangProxy=socks://<host>:<port>
```
Note:
* This proxy is only used when querying character information from Mojang; texture image downloads do not go through the proxy (even for textures from Mojang).
* Currently, only SOCKS5 is supported.
