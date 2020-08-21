 * **English**
 * [简体中文(Chinese Simplified)](https://github.com/yushijinhun/authlib-injector/blob/develop/README.md)

# authlib-injector
[![circle ci](https://img.shields.io/github/workflow/status/yushijinhun/authlib-injector/CI?style=flat-square)](https://github.com/yushijinhun/authlib-injector/actions?query=workflow%3ACI)
[![license agpl-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue.svg?style=flat-square)](https://github.com/yushijinhun/authlib-injector/blob/1caea43b49a059de4f8e44f11ede06a89a43a088/LICENSE)
![language](https://img.shields.io/badge/language-java-yellow.svg?style=flat-square)
![require java 1.8+](https://img.shields.io/badge/require%20java-1.8%2B-orange.svg?style=flat-square)

authlib-injector enables you to build a Minecraft authentication system offering all the features that genuine Minecraft has.

**[See the wiki](https://github.com/yushijinhun/authlib-injector/wiki) for documents and detailed descriptions.**

## Download
You can download the latest authlib-injector build from [here](https://authlib-injector.yushi.moe/~download/).

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
-Dauthlibinjector.mojangProxy={proxy server URL}
    Use proxy when accessing Mojang authentication service.
    Only SOCKS protocol is supported.
    URL format: socks://<host>:<port>

-Dauthlibinjector.debug (equals -Dauthlibinjector.debug=verbose,authlib)
 or -Dauthlibinjector.debug=<comma-separated debug options>
    Enable debug options.
    Available debug options:
      verbose             enable verbose logging
      authlib             print logs from Mojang authlib
      dumpClass           dump modified classes
      printUntransformed  print classes that are analyzed but not transformed, implies 'verbose'

-Dauthlibinjector.ignoredPackages={comma-separated package list}
    Ignore specified packages. Classes in these packages will not be analyzed or modified.

-Dauthlibinjector.disableHttpd
    Disable local HTTP server. Some features may not function properly.
```
