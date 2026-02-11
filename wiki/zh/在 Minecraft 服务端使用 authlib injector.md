## 获取 authlib-injector

首先你需要从[此处](https://authlib-injector.yushi.moe/)下载最新版本的 authlib-injector。

## 原版服务端 / Spigot / Paper / ...
> 自 authlib-injector v1.2.0 以后，需要设置 `enforce-secure-profile` 为 `true`，这一点与先前版本不同！
>
> 如果您在 MC 1.19+ 上遇到聊天消息签名相关的问题，请阅读：  
> :point_right: [authlib-injector v1.2.0 升级常见问题解答 FAQ #174](https://github.com/yushijinhun/authlib-injector/discussions/174) :point_left:

请将服务端 `server.properties` 中的 `online-mode` 设置为 `true`。对于 1.19+ 的服务端，还需要**设置`enforce-secure-profile` 为 `true`**。

然后在服务端的启动命令中添加以下 JVM 参数（添加的参数位于 `-jar` **之前**）：

```
-javaagent:{path/to/authlib-injector.jar}={https://your-yggdrasil-api-root.com}
```

- `{path/to/authlib-injector.jar}` 表示你在[上一步](#获取-authlib-injector)中下载的 JAR 文件所在的位置（相对路径、绝对路径皆可）。
- `{https://your-yggdrasil-api-root.com}` 表示验证服务器的 URL。

例如，这是原先的启动命令：

```
java -jar minecraft_server.1.12.2.jar nogui
```

假设：

- 你下载到的 authlib-injector JAR 文件名为 `authlib-injector.jar`。
- 你将其放到了与服务端 JAR `minecraft_server.1.12.2.jar` 相同的目录下。
- 验证服务器的 URL 为 `https://example.yggdrasil.yushi.moe`。

那么添加参数后的命令行应该如下：

```
java -javaagent:authlib-injector.jar=https://example.yggdrasil.yushi.moe -jar minecraft_server.1.12.2.jar nogui
```

## BungeeCord / Velocity
如果使用 BungeeCord 或 Velocity，那么在所有服务端**以及 BungeeCord / Velocity** 上都需要加载 authlib-injector（方法见上），同时开启 `enforce-secure-chat`。但应只有 BungeeCord / Velocity 打开 `online-mode`，后端 MC 服务端应关闭 `online-mode`。

## 调用 Mojang 皮肤
加载 authlib-injector 后，所有皮肤默认都是从指定的验证服务器处获取的。例如：
* `/give @p minecraft:skull 1 3 {SkullOwner:"notch"}`
* （Citizens2 插件）`/npc skin notch`

这些命令获取的都是**自定义的验证服务器上**名为 `notch` 的角色的皮肤。

如果要使用 Mojang 的皮肤，则可以在角色名称后加上 `@mojang`，如：
* `/give @p minecraft:skull 1 3 {SkullOwner:"notch@mojang"}`
* `/npc skin notch@mojang`

详细说明见 [README § 参数](https://github.com/yushijinhun/authlib-injector#参数) 中的 `-Dauthlibinjector.mojangNamespace` 选项。

### 通过代理访问 Mojang
调用 Mojang 皮肤的功能需要 MC 服务端能够访问 Mojang API。如果你的服务端要通过代理才能访问 Mojang，那么你可以在启动时添加以下 **JVM 参数**来指定代理：
```
-Dauthlibinjector.mojangProxy=socks://<host>:<port>
```
注意：
* 只有向 Mojang 查询角色信息时才会使用此代理，材质图像下载不走代理（即使是来自 Mojang 的材质）。
* 目前仅支持 SOCKS5。
