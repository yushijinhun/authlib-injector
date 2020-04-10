 * **English**
 * [简体中文(Chinese Simplified)](https://github.com/yushijinhun/authlib-injector/blob/develop/README.md)

# authlib-injector
[![circle ci](https://img.shields.io/circleci/project/github/yushijinhun/authlib-injector/master.svg?style=flat-square)](https://circleci.com/gh/yushijinhun/authlib-injector/tree/master)
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
The authentication server is required to implement [Yggdrasil Server Specification](https://github.com/yushijinhun/authlib-injector/wiki/Yggdrasil-%E6%9C%8D%E5%8A%A1%E7%AB%AF%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83).

Configure Minecraft server with the following JVM parameter:
```
-javaagent:{/path/to/authlib-injector.jar}={API Root of Authentication Server}
```

## Debug
### Print verbose logs
Add the following JVM parameter:
```
-Dauthlibinjector.debug={types of logs to print}
```
Types of logs:
 * `launch` startup of authlib-injector
 * `transform` bytecode modification
 * `config` configuration fetching
 * `httpd` local http server (The local http server acts as a reverse proxy between client and the remote authentication server, which allows authlib-injector to implement enhancements.)
 * `authlib` logs intercepted from authlib (which contains detailed network communication)

Use `,` as the separator when specifying multiple types. To print all the logs, set the type to `all`.

### Dump modified classes
Dump the modified classes to current directory with the following JVM parameter:
```
-Dauthlibinjector.dumpClass=true
```

