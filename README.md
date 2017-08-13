# authlib-injector
通过运行时修改authlib实现游戏外登录

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

可以通过以下方法生成一个新的RSA私钥：
```
openssl genrsa -out key.pem 4096
```

然后从私钥产生公钥：
```
openssl rsa -in key.pem -pubout
```

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

## 本项目与authlib-agent的关系
authlib-agent项目存在较多历史遗留问题，并且原项目的javaagent部分及后端部分耦合在一起，需要一起构建。因此将原项目的javaagent部分重写，并更名authlib-injector，同时提供更加友好的配置方式，以供其它yggdrasil服务端实现使用。

