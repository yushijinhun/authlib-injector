 * [English](https://github.com/yushijinhun/authlib-injector/blob/develop/README.en.md)
 * **简体中文(Chinese Simplified)**

> **This project is in maintenance mode and not accepting new features.** If you’d like to take over development, contact me through my GitHub profile: [@yushijinhun](https://github.com/yushijinhun).
>
> **本项目目前仅做维护，不再新增功能。**

# authlib-injector
[![latest release](https://img.shields.io/github/v/tag/yushijinhun/authlib-injector?color=yellow&include_prereleases&label=version&sort=semver&style=flat-square)](https://github.com/yushijinhun/authlib-injector/releases)
[![ci status](https://img.shields.io/github/actions/workflow/status/yushijinhun/authlib-injector/ci.yml?branch=develop)](https://github.com/yushijinhun/authlib-injector/actions?query=workflow%3ACI)
[![license agpl-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg?style=flat-square)](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE)

通过运行时修改 authlib 实现游戏外登录，并为 Yggdrasil 服务端的实现提供规范。

**关于该项目的详细介绍见 [wiki](https://github.com/yushijinhun/authlib-injector/wiki)。**

## 获取
您可以从[这里](https://authlib-injector.yushi.moe/)获取最新的 authlib-injector。

## 构建
构建依赖：Gradle、JDK 17+（目标 Java 版本为 8）。

执行以下命令：
```
gradle
```
构建输出位于 `build/libs` 下。

## 部署
通过添加以下 JVM 参数来配置：
```
-javaagent:{authlib-injector.jar 的路径}={验证服务器 URL (API 地址)}
```

## 参数
```
-Dauthlibinjector.noLogFile
    不要将日志输出到文件.
    默认情况下, authlib-injector 会将日志输出到控制台以及当前目录下的 authlib-injector.log 文件.
    开启此选项后, 日志仅会输出到控制台.

    需要注意的是, authlib-injector 的日志是不会输出到 Minecraft 服务端/客户端的日志文件中的.

    每次启动时, 日志文件都会被清空. 如果有多个进程使用同一个日志文件, 则只有最早启动的会成功打开日志文件.

-Dauthlibinjector.mojangNamespace={default|enabled|disabled}
    设置是否启用 Mojang 命名空间 (@mojang 后缀).
    若验证服务器未设置 feature.no_mojang_namespace 选项, 则该功能默认启用.

    启用后, 则可以使用名为 <username>@mojang 的虚拟角色来调用对应正版角色的皮肤.
    例如,
     - /give @p minecraft:skull 1 3 {SkullOwner:"Notch@mojang"}
     - /npc skin Notch@mojang
    显示的将会是 Notch 的皮肤.

    注意, 虚拟角色和对应正版角色的 UUID 是不同的. 为了将虚拟角色和正版角色区别开,
    虚拟角色 UUID 中 time_hi_and_version 字段的最高位被置为 1 (见 RFC 4122 4.1.3 章节).
    例如:
      069a79f4-44e9-4726-a5be-fca90e38aaf5 Notch
      069a79f4-44e9-c726-a5be-fca90e38aaf5 Notch@mojang
    采用该方法的原因是, 在 RFC 4122 中 UUID 版本号只有 6 种可能的取值 (0~5), 版本号的最高位始终为 0.
    而实际上, Mojang 使用的是版本 4 (随机) UUID, 因此其对应的虚拟角色的 UUID 版本号为 12.

-Dauthlibinjector.mojangProxy={代理服务器 URL}
    设置访问 Mojang 验证服务时使用的代理, 目前仅支持 SOCKS 协议.
    URL 格式: socks://<host>:<port>

    这一代理仅作用于 Mojang 命名空间 功能, 其仅用于访问 Mojang 服务器.
    若要在访问自定义验证服务器时使用代理, 请参考 https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html .

-Dauthlibinjector.legacySkinPolyfill={default|enabled|disabled}
    是否启用旧式皮肤 API polyfill, 即 'GET /skins/MinecraftSkins/{username}.png'.
    若验证服务器未设置 feature.legacy_skin_api 选项, 则该功能默认启用.

-Dauthlibinjector.debug (等价于 -Dauthlibinjector.debug=verbose,authlib)
 或 -Dauthlibinjector.debug={调试选项; 逗号分隔}
    可用的调试选项:
     - verbose             详细日志
     - authlib             开启 Mojang authlib 的调试输出
     - dumpClass           转储修改过的类
     - printUntransformed  打印已分析但未修改的类; 隐含 verbose

-Dauthlibinjector.ignoredPackages={包列表; 逗号分隔}
    忽略指定的包, 其中的类将不会被分析或修改.

-Dauthlibinjector.disableHttpd
    禁用内建的 HTTP 服务器.
    以下依赖内建 HTTP 服务器的功能将不可用:
     - Mojang 命名空间
     - 旧式皮肤 API polyfill

-Dauthlibinjector.httpdPort={端口号}
    设置内置 HTTP 服务器使用的端口号, 默认为 0 (随机分配).

-Dauthlibinjector.noShowServerName
    不要在 Minecraft 主界面展示验证服务器名称.
    默认情况下, authlib-injector 通过更改 --versionType 参数来在 Minecraft 主界面显示验证服务器名称, 使用本选项可以禁用该功能.

-Dauthlibinjector.mojangAntiFeatures={default|enabled|disabled}
    设置是否开启 Minecraft 的部分 anti-feature.
    若验证服务器未设置 feature.enable_mojang_anti_features 选项, 则默认禁用.

  Minecraft 的 anti-feature 包括:
     - Minecraft 服务器屏蔽列表
     - 查询用户权限的接口, 涵盖以下项目:
       * 聊天权限 (禁用后默认允许)
       * 多人游戏权限 (禁用后默认允许)
       * 领域权限 (禁用后默认允许)
       * 遥测 (禁用后默认关闭)
       * 冒犯性内容过滤 (禁用后默认关闭)

-Dauthlibinjector.profileKey={default|enabled|disabled}
    是否启用消息签名密钥对功能, 这一功能在 22w17a 引入, 用于多人游戏中聊天消息的数字签名.
    启用此功能后, Minecraft 会向 /minecraftservices/player/certificates 发送 POST 请求, 以获取由验证服务器颁发的密钥对.
    此功能需要验证服务器支持, 若验证服务器未设置 feature.enable_profile_key 选项, 则该功能默认禁用.

-Dauthlibinjector.usernameCheck={default|enabled|disabled}
    是否启用玩家用户名检查, 若禁用, 则 authlib-injector 将关闭 Minecraft、BungeeCord 和 Paper 的用户名检查功能.
    若验证服务器未设置 feature.usernameCheck 选项, 则默认禁用.
    注意, 开启此功能将导致用户名包含非英文字符的玩家无法进入服务器.
```

## 捐助
BMCLAPI 为 authlib-injector 提供了[下载镜像站](https://github.com/yushijinhun/authlib-injector/wiki/%E8%8E%B7%E5%8F%96-authlib-injector#bmclapi-%E9%95%9C%E5%83%8F)。如果您想要支持 authlib-injector 的开发，您可以[捐助 BMCLAPI](https://bmclapidoc.bangbang93.com/)。

## 许可
本程序使用 [GNU Affero General Public License v3.0 or later](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE) 许可，并附有以下例外：

> **AGPL 的例外情况:**
>
> 作为特例，如果您的程序通过以下方式利用本作品，则相应的行为不会导致您的作品被 AGPL 协议涵盖。
> 1. 您的程序通过打包的方式包含本作品未经修改的二进制形式，而没有静态或动态地链接到本作品；或
> 2. 您的程序通过本作品提供的进程间通信接口（如 HTTP API）进行交互；或
> 3. 您的程序将本作品作为 Java Agent 加载进 Java 虚拟机。
