 * **English**
 * [简体中文(Chinese Simplified)](https://github.com/yushijinhun/authlib-injector/blob/develop/README.md)

> **This project is in maintenance mode and not accepting new features.** If you’d like to take over development, contact me through my GitHub profile: [@yushijinhun](https://github.com/yushijinhun).

# authlib-injector
[![latest release](https://img.shields.io/github/v/tag/yushijinhun/authlib-injector?color=yellow&include_prereleases&label=version&sort=semver&style=flat-square)](https://github.com/yushijinhun/authlib-injector/releases)
[![ci status](https://img.shields.io/github/actions/workflow/status/yushijinhun/authlib-injector/ci.yml?branch=develop)](https://github.com/yushijinhun/authlib-injector/actions?query=workflow%3ACI)
[![license agpl-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg?style=flat-square)](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE)

authlib-injector enables you to build a Minecraft authentication system offering all the features that genuine Minecraft has.

**[See the wiki](https://github.com/yushijinhun/authlib-injector/wiki) for documents and detailed descriptions.**

## Download
You can download the latest authlib-injector build from [here](https://authlib-injector.yushi.moe/).

## Build
Dependencies: Gradle, JDK 17+. The target Java platform version is 8.

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

-Dauthlibinjector.httpdPort={port}
    Sets the port used by the local HTTP server, defaults to 0 (randomly chosen).

-Dauthlibinjector.noShowServerName
    Do not show authentication server name in Minecraft menu screen.
    By default, authlib-injector alters --versionType parameter to display the authentication server name.
    This feature can be disabled using this option.

-Dauthlibinjector.mojangAntiFeatures={default|enabled|disabled}
    Whether to turn on Minecraft's anti-features.
    It's disabled by default if the authentication server does NOT send feature.enable_mojang_anti_features option.

    These anti-features include:
     - Minecraft server blocklist
     - The API to query user privileges:
       * Online chat (allowed if the option is disabled)
       * Multiplayer (allowed if the option is disabled)
       * Realms (allowed if the option is disabled)
       * Telemetry (turned off if the option is disabled)
       * Profanity filter (turned off if the option is disabled)

-Dauthlibinjector.profileKey={default|enabled|disabled}
    Whether to enable the profile signing key feature. This feature is introduced in 22w17a, and is used to implement the multiplayer secure chat signing.
    If this this feature is enabled, Minecraft will send a POST request to /minecraftservices/player/certificates to retrieve the key pair issued by the authentication server.
    It's disabled by default if the authentication server does NOT send feature.enable_profile_key option.

-Dauthlibinjector.usernameCheck={default|enabled|disabled}
    Whether to enable username validation. If disabled, Minecraft, BungeeCord and Paper will NOT perform username validation.
    It's disabled by default if the authentication server does NOT send feature.usernameCheck option.
    Turning on this option will prevent players whose username contains special characters from joining the server.
```

## License
This work is licensed under the [GNU Affero General Public License v3.0](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE) or later, with the "AUTHLIB-INJECTOR" exception.

> **"AUTHLIB-INJECTOR" EXCEPTION TO THE AGPL**
>
> As a special exception, using this work in the following ways does not cause your program to be covered by the AGPL:
> 1. Bundling the unaltered binary form of this work in your program without statically or dynamically linking to it; or
> 2. Interacting with this work through the provided inter-process communication interface, such as the HTTP API; or
> 3. Loading this work as a Java Agent into a Java Virtual Machine.
