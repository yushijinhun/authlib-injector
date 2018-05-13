# authlib-injector
[![Jenkins](https://img.shields.io/jenkins/s/https/ci.to2mbn.org/job/authlib-injector.svg?style=flat-square)](https://ci.to2mbn.org/job/authlib-injector/)
[![license](https://img.shields.io/github/license/to2mbn/authlib-injector.svg?style=flat-square)](https://github.com/to2mbn/authlib-injector/blob/master/LICENSE)
![language](https://img.shields.io/badge/language-java-yellow.svg?style=flat-square)
![require java 1.8+](https://img.shields.io/badge/require%20java-1.8%2B-orange.svg?style=flat-square)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fto2mbn%2Fauthlib-injector.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fto2mbn%2Fauthlib-injector?ref=badge_shield)

通过运行时修改authlib实现游戏外登录，并为Yggdrasil服务端的实现提供规范

关于该项目的详细介绍见[wiki](https://github.com/to2mbn/authlib-injector/wiki)。

## 编译
```
gradle
```
构建输出位于`build/libs`下。

或者直接从[Jenkins](https://ci.to2mbn.org/job/authlib-injector)下载构建好的JAR。

## 部署
需要服务端实现本规范中的[扩展API](https://github.com/to2mbn/authlib-injector/wiki/Yggdrasil%E6%9C%8D%E5%8A%A1%E7%AB%AF%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83#%E6%89%A9%E5%B1%95api)。
通过添加以下JVM参数来配置：
```
-javaagent:{authlib-injector.jar的路径}={Yggdrasil服务端的URL（API Root）}
```


## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fto2mbn%2Fauthlib-injector.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fto2mbn%2Fauthlib-injector?ref=badge_large)