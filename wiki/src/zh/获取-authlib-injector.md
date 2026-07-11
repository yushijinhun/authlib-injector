# 获取 authlib injector

## 手动下载
authlib-injector 的最新版本可以直接从 [authlib-injector.yushi.moe](https://authlib-injector.yushi.moe/) 下载。

## 下载 API
authlib-injector 项目提供了一组 API 用于下载 authlib-injector 构件。其 API 入口为 [`https://authlib-injector.yushi.moe/`](https://authlib-injector.yushi.moe/)。

### 获取版本列表
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

### 获取特定版本
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

### 获取最新版本
`GET /artifact/latest.json`

响应格式[同上](#获取特定版本)。如果你只需要获取最新版本，使用此 API 足矣。

### BMCLAPI 镜像
> 使用 BMCLAPI 时请遵守 [BMCLAPI 的协议](https://bmclapidoc.bangbang93.com/#api-_)。

BMCLAPI 为本下载 API 提供了一个[镜像](https://bmclapidoc.bangbang93.com/#api-Mirrors-Mirrors_authlib_injector)，其入口为 [`https://bmclapi2.bangbang93.com/mirrors/authlib-injector/`](https://bmclapi2.bangbang93.com/mirrors/authlib-injector/)。
