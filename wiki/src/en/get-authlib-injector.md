# Get authlib-injector

## Manual Download
The latest version of authlib-injector can be downloaded directly from [authlib-injector.yushi.moe](https://authlib-injector.yushi.moe/).

## Download API
The authlib-injector project provides a set of APIs for downloading authlib-injector artifacts. The API entry point is [`https://authlib-injector.yushi.moe/`](https://authlib-injector.yushi.moe/).

### Get version list
`GET /artifacts.json`

Response format:
```javascript
{
	"latest_build_number": build number of the latest version,
	"artifacts": [ // version information
		{
			"build_number": build number of this version,
			"version": "version number of this version"
		}
		// ,... (more items may follow)
	]
}
```

### Get a specific version
`GET /artifact/{build_number}.json`

The `{build_number}` parameter in the URL represents the build number of the version.

Response format:
```javascript
{
	"build_number": build number of this version,
	"version": "version number of this version",
	"download_url": "download URL of this version of authlib-injector",
	"checksums": { // checksums
		"sha256": "SHA-256 checksum"
	}
}
```

### Get latest version
`GET /artifact/latest.json`

Response format [same as above](#get-specific-version). If you only need to get the latest version, this API is sufficient.

### BMCLAPI Mirror
> Please comply with the [BMCLAPI terms](https://bmclapidoc.bangbang93.com/#api-_) when using BMCLAPI.

BMCLAPI provides a [mirror](https://bmclapidoc.bangbang93.com/#api-Mirrors-Mirrors_authlib_injector) for this download API, with the entry point at [`https://bmclapi2.bangbang93.com/mirrors/authlib-injector/`](https://bmclapi2.bangbang93.com/mirrors/authlib-injector/).
