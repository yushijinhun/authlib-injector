 * **English**
 * [简体中文(Chinese Simplified)](https://github.com/yushijinhun/authlib-injector/blob/develop/README.md)

# authlib-injector
[![latest release](https://img.shields.io/github/v/tag/yushijinhun/authlib-injector?color=yellow&include_prereleases&label=version&sort=semver&style=flat-square)](https://github.com/yushijinhun/authlib-injector/releases)
[![ci status](https://img.shields.io/github/workflow/status/yushijinhun/authlib-injector/CI?style=flat-square)](https://github.com/yushijinhun/authlib-injector/actions?query=workflow%3ACI)
[![license agpl-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg?style=flat-square)](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE)

authlib-injector enables you to build a Minecraft authentication system offering all the features that genuine Minecraft has.

**[See the wiki](https://github.com/yushijinhun/authlib-injector/wiki) for documents and detailed descriptions.**

## Download
You can download the latest authlib-injector build from [here](https://authlib-injector.yushi.moe/).

## Build
Dependencies: Gradle, JDK 8+

Run:
```
gradle
```
Build output can be found in `build/libs`.

## Deploy
Configure Minecraft server with the following JVM parameter:
```
-javaagent:{/path/to/authlib-injector.jar}={Authentication Server URL}
```

## Options
```
-Dauthlibinjector.mojangNamespace={default|enabled|disabled}
    Whether to enable Mojang namespace (@mojang suffix).
    It's enabled by default if the authentication server does NOT send feature.no_mojang_namespace option.

    If enabled, virtual player <username>@mojang will have the same skin as premium (Mojang) player <username>.
    For example,
     - /give @p minecraft:skull 1 3 {SkullOwner:"Notch@mojang"}
     - /npc skin Notch@mojang
    will display Notch's skin.

    Note that the virtual player does NOT have the same UUID as its corresponding premium player.
    To distinguish virtual players from actual ones, the most significant bit of time_hi_and_version is set to 1 (see RFC 4122 section 4.1.3).
    For example:
      069a79f4-44e9-4726-a5be-fca90e38aaf5 Notch
      069a79f4-44e9-c726-a5be-fca90e38aaf5 Notch@mojang
    We use this approach because, in RFC 4122, UUID version has only 6 possible values (0~5), which means the most significant is always 0.
    In fact, Mojang uses version-4 (random) UUID, so its corresponding virtual player has a version-12 UUID.

-Dauthlibinjector.mojangProxy={proxy server URL}
    Use proxy when accessing Mojang authentication service.
    Only SOCKS protocol is supported.
    URL format: socks://<host>:<port>

    This proxy setting only affects Mojang namespace feature, and the proxy is used only when accessing Mojang's servers.
    To enable proxy for your customized authentication server, see https://docs.oracle.com/javase/8/docs/technotes/guides/net/proxies.html .

-Dauthlibinjector.legacySkinPolyfill={default|enabled|disabled}
    Whether to polyfill legacy skin API, namely 'GET /skins/MinecraftSkins/{username}.png'.
    It's enabled by default if the authentication server does NOT send feature.legacy_skin_api option.

-Dauthlibinjector.debug (equals -Dauthlibinjector.debug=verbose,authlib)
 or -Dauthlibinjector.debug={comma-separated debug options}
    Available debug options:
     - verbose             enable verbose logging
     - authlib             print logs from Mojang authlib
     - dumpClass           dump modified classes
     - printUntransformed  print classes that are analyzed but not transformed, implies 'verbose'

-Dauthlibinjector.ignoredPackages={comma-separated package list}
    Ignore specified packages. Classes in these packages will not be analyzed or modified.

-Dauthlibinjector.disableHttpd
    Disable local HTTP server.
    Features (see below) depending on local HTTP server will be unavailable:
     - Mojang namespace
     - Legacy skin API polyfill

-Dauthlibinjector.noShowServerName
    Do not show authentication server name in Minecraft menu screen.
    By default, authlib-injector alters --versionType parameter to display the authentication server name.
    This feature can be disabled using this option.
    
-Dauthlibinjector.trustUnknownSSLCertificates
    Add this parameter to trust unknown SSL certificates.
    Do not use this option unless you have a problem with the certificate,
    this may affect the security of the connection.
```
