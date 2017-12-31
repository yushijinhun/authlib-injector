# authlib-injector
[![Jenkins](https://img.shields.io/jenkins/s/https/ci.to2mbn.org/job/authlib-injector.svg?style=flat-square)](https://ci.to2mbn.org/job/authlib-injector/)
[![license](https://img.shields.io/github/license/to2mbn/authlib-injector.svg?style=flat-square)](https://github.com/to2mbn/authlib-injector/blob/master/LICENSE)
![language](https://img.shields.io/badge/language-java-yellow.svg?style=flat-square)
![require java 1.8+](https://img.shields.io/badge/require%20java-1.8%2B-orange.svg?style=flat-square)

通过运行时修改authlib实现游戏外登录，并为Yggdrasil服务端的实现提供规范

关于该项目的详细介绍见[wiki](https://github.com/to2mbn/authlib-injector/wiki)。

## 编译
```
gradle clean shadowJar
```
构建输出位于`build/libs`下。

或者直接从[Jenkins](https://ci.to2mbn.org/job/authlib-injector)下载构建好的JAR。

## 部署

### 配置
配置文件模板位于[authlib-injector.example.yaml](https://github.com/to2mbn/authlib-injector/blob/master/authlib-injector.example.yaml)。

#### 生成签名公钥
服务端返回的profile properties需要带有数字签名。

生成方法见[签名密钥对](https://github.com/to2mbn/authlib-injector/wiki/%E7%AD%BE%E5%90%8D%E5%AF%86%E9%92%A5%E5%AF%B9)。

### 加载
#### 作为javaagent加载
向JVM参数中添加`-javaagent:<path_to_authlib-injector.jar>`

该方法适用于所有客户端、服务端、启动器等。

#### 作为mod加载
直接放入mods目录即可。

该方法适用于Forge及Liteloader。

### 指定配置文件
authlib-injector提供了以下方式来指定配置文件（按优先级排序）：

1. 通过javaagent参数指定
   * 在`javaagent`参数后面添加`=<path_to_config>`
   * 例如`-javaagent:authlib-injector.jar=my-authlib-injector.yaml`
   * 仅适用于通过javaagent加载
2. 通过`org.to2mbn.authlibinjector.config`属性指定
   * 如`-Dorg.to2mbn.authlibinjector.config=my-authlib-injector.yaml`
3. JAR中的`/authlib-injector.yaml`文件
   * 可以在编译时向`src/main/resources`中添加配置文件，或者直接向JAR中添加（JAR为zip格式）
4. 当前目录下的`authlib-injector.yaml`文件

### 远程自动配置
对于实现了本规范中[扩展API](https://github.com/to2mbn/authlib-injector/wiki/Yggdrasil%E6%9C%8D%E5%8A%A1%E7%AB%AF%E6%8A%80%E6%9C%AF%E8%A7%84%E8%8C%83#%E6%89%A9%E5%B1%95api)的Yggdrasil服务端，可以直接通过添加以下JVM参数来配置，不需要配置文件：
```
-javaagent:{authlib-injector.jar的路径}=@{Yggdrasil服务端的URL（API Root）}
```
