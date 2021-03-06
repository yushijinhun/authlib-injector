<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
目录
=================

- [手动下载](#%E6%89%8B%E5%8A%A8%E4%B8%8B%E8%BD%BD)
- [下载 API](#%E4%B8%8B%E8%BD%BD-api)
  - [获取版本列表](#%E8%8E%B7%E5%8F%96%E7%89%88%E6%9C%AC%E5%88%97%E8%A1%A8)
  - [获取特定版本](#%E8%8E%B7%E5%8F%96%E7%89%B9%E5%AE%9A%E7%89%88%E6%9C%AC)
  - [获取最新版本](#%E8%8E%B7%E5%8F%96%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)
  - [BMCLAPI 镜像](#bmclapi-%E9%95%9C%E5%83%8F)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# 手动下载
authlib-injector 的最新版本可以直接从 [authlib-injector.yushi.moe](https://authlib-injector.yushi.moe/) 下载。

# 下载 API
authlib-injector 项目提供了一组 API 用于下载 authlib-injector 构件。其 API 入口为 [`https://authlib-injector.yushi.moe/`](https://authlib-injector.yushi.moe/)。

## 获取版本列表
`GET /artifacts.json`

响应格式：
```javascript
{
	"latest_build_number": 最新版本的构建号,
	"artifacts": [ // 各版本信息
		{
			"build_number": 此版本的构建号,
			"version": "此版本的版本号"
		}
		// ,... （可以有更多）
	]
}
```

## 获取特定版本
`GET /artifact/{build_number}.json`

URL 中的 `{build_number}` 参数代表版本构建号。

响应格式：
```javascript
{
	"build_number": 此版本的构建号,
	"version": "此版本的版本号",
	"download_url": "此版本的 authlib-injector 的下载地址",
	"checksums": { // 校验和
		"sha256": "SHA-256 校验和"
	}
}
```

## 获取最新版本
`GET /artifact/latest.json`

响应格式[同上](#获取特定版本)。如果你只需要获取最新版本，使用此 API 足矣。

## BMCLAPI 镜像
> 使用 BMCLAPI 时请遵守 [BMCLAPI 的协议](https://bmclapidoc.bangbang93.com/#api-_)。

BMCLAPI 为本下载 API 提供了一个[镜像](https://bmclapidoc.bangbang93.com/#api-Mirrors-Mirrors_authlib_injector)，其入口为 [`https://bmclapi2.bangbang93.com/mirrors/authlib-injector/`](https://bmclapi2.bangbang93.com/mirrors/authlib-injector/)。
