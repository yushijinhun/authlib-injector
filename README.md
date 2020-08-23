 * [English](https://github.com/yushijinhun/authlib-injector/blob/develop/README.en.md)
 * **简体中文(Chinese Simplified)**

# authlib-injector
[![circle ci](https://img.shields.io/github/workflow/status/yushijinhun/authlib-injector/CI?style=flat-square)](https://github.com/yushijinhun/authlib-injector/actions?query=workflow%3ACI)
[![license agpl-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg?style=flat-square)](https://github.com/yushijinhun/authlib-injector/blob/1caea43b49a059de4f8e44f11ede06a89a43a088/LICENSE)
![language](https://img.shields.io/badge/language-java-yellow.svg?style=flat-square)
![require java 1.8+](https://img.shields.io/badge/require%20java-1.8%2B-orange.svg?style=flat-square)

通过运行时修改 authlib 实现游戏外登录，并为 Yggdrasil 服务端的实现提供规范。

**关于该项目的详细介绍见 [wiki](https://github.com/yushijinhun/authlib-injector/wiki)。**

## 获取
您可以从[这里](https://authlib-injector.yushi.moe/~download/)获取最新的 authlib-injector。

## 构建
构建依赖：Gradle、JDK 8+。

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
```

## 捐助
BMCLAPI 为 authlib-injector 提供了[下载镜像站](https://github.com/yushijinhun/authlib-injector/wiki/%E8%8E%B7%E5%8F%96-authlib-injector#bmclapi-%E9%95%9C%E5%83%8F)。如果您想要支持 authlib-injector 的开发，您可以[捐助 BMCLAPI](https://bmclapidoc.bangbang93.com/)。
