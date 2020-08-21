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
-javaagent:{authlib-injector.jar 的路径}={验证服务器 URL（API 地址）}
```

## 参数
```
-Dauthlibinjector.mojangProxy={代理服务器 URL}
    设置访问 Mojang 验证服务时使用的代理，目前仅支持 SOCKS 协议
    URL 格式为 socks://<host>:<port>

-Dauthlibinjector.debug (等价于 -Dauthlibinjector.debug=verbose,authlib)
 或 -Dauthlibinjector.debug={调试选项; 逗号分隔}
    开启调试功能
    可用的调试选项：
      verbose             详细日志
      authlib             开启 Mojang authlib 的调试输出
      dumpClass           转储修改过的类
      printUntransformed  打印已分析但未修改的类，暗含 verbose

-Dauthlibinjector.ignoredPackages={包列表; 逗号分隔}
    忽略指定的包，其中的类将不会被分析或修改

-Dauthlibinjector.disableHttpd
    禁用内建的 HTTP 服务器，可能导致部分功能不正常
```

## 捐助
BMCLAPI 为 authlib-injector 提供了[下载镜像站](https://github.com/yushijinhun/authlib-injector/wiki/%E8%8E%B7%E5%8F%96-authlib-injector#bmclapi-%E9%95%9C%E5%83%8F)。如果您想要支持 authlib-injector 的开发，您可以[捐助 BMCLAPI](https://bmclapidoc.bangbang93.com/)。
