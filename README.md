# authlib-injector
[![circle ci](https://img.shields.io/circleci/project/github/yushijinhun/authlib-injector/master.svg?style=flat-square)](https://circleci.com/gh/yushijinhun/authlib-injector/tree/master)
[![license](https://img.shields.io/github/license/yushijinhun/authlib-injector.svg?style=flat-square)](https://github.com/yushijinhun/authlib-injector/blob/master/LICENSE)
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
需要服务端实现本规范中的[扩展 API](https://github.com/yushijinhun/authlib-injector/wiki/Yggdrasil%E6%9C%8D%E5%8A%A1%E7%AB%AF%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83#%E6%89%A9%E5%B1%95-api)。
通过添加以下 JVM 参数来配置：
```
-javaagent:{authlib-injector.jar 的路径}={Yggdrasil 服务端的 URL（API Root）}
```

## 调试
### 调试输出
添加以下 JVM 参数：
```
-Dauthlibinjector.debug={要打印的调试信息类型}
```
调试信息类型有：
 * `launch` 有关 authlib-injector 加载的
 * `transform` 有关字节码修改的
 * `config` 有关配置获取的
 * `httpd` 有关本地 HTTP 服务器的（其负责在本地处理掉部分请求，而不是发送到 Yggdrasil 服务端）
 * `authlib` 打印从 authlib 处获取的日志（其记录了网络调用的详细信息）

可以指定多个类型，中间用 `,` 分隔。如果要打印以上所有调试信息，可以设置其为 `all`。

### 保存修改过的类
添加以下 JVM 参数：
```
-Dauthlibinjector.dumpClass=true
```
修改过的类文件会保存在当前目录下。

## 捐助
BMCLAPI 为 authlib-injector 提供了[下载镜像站](https://github.com/yushijinhun/authlib-injector/wiki/%E8%8E%B7%E5%8F%96-authlib-injector#bmclapi-%E9%95%9C%E5%83%8F)。如果您想要支持 authlib-injector 的开发，您可以[捐助 BMCLAPI](https://bmclapidoc.bangbang93.com/)。
