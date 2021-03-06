<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
目录

- [简介](#%E7%AE%80%E4%BB%8B)
- [相关项目](#%E7%9B%B8%E5%85%B3%E9%A1%B9%E7%9B%AE)
- [推荐的公共验证服务器](#%E6%8E%A8%E8%8D%90%E7%9A%84%E5%85%AC%E5%85%B1%E9%AA%8C%E8%AF%81%E6%9C%8D%E5%8A%A1%E5%99%A8)
- [捐助](#%E6%8D%90%E5%8A%A9)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## 简介
该项目的目标：
 * 为修改 Minecraft ，使其使用自定义 Yggdrasil 服务提供工具
 * 为自定义 Yggdrasil 服务端、使用自定义 Yggdrasil 服务的启动器提供技术规范
 * 为玩家提供统一的非 Mojang 游戏外登录体验
   * 玩家可以使用任意实现该规范的启动器，登录任意实现该规范的 Yggdrasil 服务

该项目会对所有 API 作出详细说明，并且还会定义一些不属于 Yggdrasil 的 API 。这样做是为了最简化指定 Yggdrasil 服务的流程：只需要填写 Yggdrasil 服务对应的 URL ，就可以使用它。

如果你是 Yggdrasil 服务端开发者或启动器开发者，或是对本项目感兴趣，请 Watch 本项目，以了解规范最新的发展

> 开发者交流 QQ 群：926979364，Telegram 群：[@authlib_injector](https://t.me/authlib_injector)。欢迎启动器或皮肤站开发者加入。普通用户请勿加群，可能会听不懂。

## 相关项目
 * [yggdrasil-mock](https://github.com/yushijinhun/yggdrasil-mock)
   * Yggdrasil 服务端规范的参考实现，以及 Yggdrasil API 的测试用例
   * 基于此项目的 Yggdrasil 服务端演示站点：[auth-demo.yushi.moe](https://github.com/yushijinhun/yggdrasil-mock/wiki/演示站点)
 * [BMCLAPI](https://bmclapidoc.bangbang93.com/#api-Mirrors-Mirrors_authlib_injector)
   * BMCLAPI 为 authlib-injector 下载提供了一个镜像
 * [Yggdrasil API for Blessing Skin](https://blessing.netlify.app/yggdrasil-api/)
   * Blessing Skin 皮肤站的 Yggdrasil 插件
 * [HMCL](https://github.com/huanghongxun/HMCL)
   * HMCL v3.x 支持 authlib-injector
 * [BakaXL](https://www.bakaxl.com/)
   * BakaXL 3.0 支持 authlib-injector
 * [LaunchHelper](https://github.com/Codex-in-somnio/LaunchHelper)
   * 想在 Multicraft 面板服上使用 authlib-injector，可以尝试此项目

## 推荐的公共验证服务器
 * [LittleSkin](https://littlesk.in/)
 * [Ely.by](https://ely.by/)

## 捐助
BMCLAPI 为 authlib-injector 提供了下载镜像站。如果您想要支持 authlib-injector 的开发，您可以[捐助 BMCLAPI](https://bmclapidoc.bangbang93.com/)。
