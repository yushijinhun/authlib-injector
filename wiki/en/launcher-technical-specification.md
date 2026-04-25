# Launcher Technical Specification

## Overview
This document aims to provide technical guidance for implementing the authlib-injector specification in launchers. Since this functionality requires calling the Yggdrasil API, it is recommended that you read the [Yggdrasil Server Technical Specification](yggdrasil-server-technical-specification.md) before reading this document.

In launchers, this login method can be referred to as "External Login (authlib-injector)" or "authlib-injector Login". We recommend using the former, which has a clearer meaning.

## Authentication Server
The authentication server (i.e., the Yggdrasil server) is the core of the entire authentication system. All authentication-related requests will be sent to it.

To identify an authentication server, the launcher should store the API address of that authentication server (i.e., the API root, such as `https://example.com/api/yggdrasil/`).

A launcher can support only one authentication server, or it can support multiple authentication servers. Supporting multiple authentication servers means that multiple accounts can exist in the launcher simultaneously, and these accounts can belong to different authentication servers.

### Authentication Server Configuration
The operation of configuring an authentication server is typically performed by the player, but there are also cases where the server owner performs the configuration and distributes the configuration file along with the launcher and game. The following describes several ways to configure an authentication server:

#### Specifying in a Configuration File
The launcher can store the API address directly in a configuration file, allowing users to configure the authentication server by modifying the configuration file. This configuration method is simple to implement; if your launcher is only used as a dedicated launcher for a server, you can use this configuration method.

#### Entering Address in Launcher
With this configuration method, the user completes the authentication server configuration by entering a URL in the launcher. This URL may be a complete API address (such as `https://example.com/api/yggdrasil/`), or it may be an abbreviated address (such as `example.com`).

When the URL does not specify a protocol (HTTPS or HTTP), we conventionally autocomplete it to the **HTTPS** protocol. In other words, `example.com/api/yggdrasil/` should be understood as `https://example.com/api/yggdrasil/`.

> For security reasons, the launcher must **not** downgrade to plaintext HTTP protocol even if it cannot connect via HTTPS protocol.

Additionally, authlib-injector defines a service discovery mechanism called API Location Indication (ALI). It is used to convert abbreviated, incomplete addresses entered by users into complete API addresses.

##### Handling API Location Indication (ALI)
To resolve the address entered by the user to the actual API address, the launcher needs to perform the following operations:
1. If the URL is missing a protocol, autocomplete it to HTTPS protocol.
2. Send a GET request to the URL (following HTTP redirects).
3. If the response contains an ALI header (HTTP header `X-Authlib-Injector-API-Location`), then the URL pointed to by the ALI is the API address.
    * `X-Authlib-Injector-API-Location` can be an absolute URL or a relative URL.
    * If the ALI points to itself, it means the current URL is the API address.
4. If the response does not contain an ALI header, the current URL is assumed to be the API address by default.

Pseudocode:
```
function resolve_api_url(url)
    response = http_get(url) // follow redirects

    if response.headers["x-authlib-injector-api-location"] exists
        new_url = to_absolute_url(response.headers["x-authlib-injector-api-location"])
        if new_url != url
            return new_url

    // if you are going to fetch the metadata next, 'response' can be reused
    return url
```

#### Configuration via Drag and Drop
This method allows users to configure the authentication server via [drag and drop (DnD)](https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API).

The DnD source can be a browser or other application, and the DnD target is the launcher. The DnD source needs to display text, an image, or other content to indicate to the user that this content should be dragged into the launcher to add an authentication server. During this process, authentication server information is transferred from the DnD source to the launcher. After completing the DnD action, the launcher asks the user to confirm whether to add this authentication server.

##### Drag Data
The MIME type of the drag data is `text/plain`, and the content is a URI in the following format:

```
authlib-injector:yggdrasil-server:{API address of the authentication server}
```
The API address is a component of the URI and should be [encoded](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent).

The drag effect is copy (`copy`).

##### HTML Example
> [Demo Page](https://rawcdn.githack.com/yushijinhun/authlib-injector/refs/heads/develop/wiki/yggdrasil-server-dnd-example.html)

Add `draggable="true"` to the DOM node that needs to be dragged, and handle the `dragstart` event:
```html
<span id="dndLabel" draggable="true" ondragstart="dndLabel_dragstart(event);">example.yggdrasil.yushi.moe</span>
```

```javascript
function dndLabel_dragstart(event) {
	let yggdrasilApiRoot = "https://example.yggdrasil.yushi.moe/";
	let uri = "authlib-injector:yggdrasil-server:" + encodeURIComponent(yggdrasilApiRoot);
	event.dataTransfer.setData("text/plain", uri);
	event.dataTransfer.dropEffect = "copy";
}
```

### Authentication Server Information Display
By sending a GET request to the API address, the launcher can obtain metadata of the authentication server ([Response Format](yggdrasil-server-technical-specification.md#api-metadata-retrieval)), such as the server name. The launcher can use this metadata to improve user experience.

#### Server Name Display
The authentication server specifies its name in `serverName` within `meta`. When the launcher needs to display an authentication server to the user, it can use this name.

Note that authentication server names may conflict, so the launcher should provide a way to view the authentication server API address. For example, when the mouse hovers over the authentication server name, the launcher displays its API address in a tooltip.

### Warning for Non-HTTPS Authentication Servers
When a user attempts to configure an authentication server using plaintext HTTP protocol, the launcher should display a prominent warning to the user, informing them that this may put their information security at risk and that the user's password will be transmitted in plaintext.

## Account
An account corresponds to a player in the game. Users can select an account to play when launching the game.

> **Relationship between Account, User, and Profile**: The concept of an account in the launcher is **not the same** as the concept of a user in the authentication server. What corresponds to an account in the launcher is a profile in the authentication server. A user in the authentication server is the owner of one or more profiles and has no corresponding entity in the launcher.

### Account Information Storage
The launcher identifies an account through the following three immutable attributes:
* The authentication server to which the account belongs
* The account's identifier (such as an email address)
  * Typically, the account identifier is the email address. However, if the `feature.non_email_login` field in the metadata returned by the authentication server is true, it indicates that the authentication server supports logging in with credentials other than an email address, meaning the account identifier may not be an email. In this case, the launcher should **not** assume that the account entered by the user is always an email address, and should be careful with wording (e.g., using "Account" instead of "Email") to avoid confusion. (See [Yggdrasil Server Technical Specification § Login with Profile Name](yggdrasil-server-technical-specification.md#login-with-profile-name))
* The UUID of the profile corresponding to the account

Two accounts are the same only when all three of the above attributes are the same; one attribute being the same does not mean the two accounts are the same. Multiple profiles can exist on the same authentication server; multiple profiles can belong to the same user; profiles with the same UUID can also appear on different authentication servers. Therefore, the launcher should use **all three** attributes to identify an account.

In addition to the above three attributes, an account also has the following attributes:
* Tokens (accessToken and clientToken)
* The name of the profile corresponding to the account
* The user's ID
* The user's properties

> [!WARNING]
> The "remember login state" feature stores tokens, **not** the user's password. The password should **never** be stored in plaintext at any time.

The above attributes are all mutable. After each login or refresh operation, the launcher needs to update the stored account attributes.

In all the login and refresh operations below, the `requestUser` parameter in the request is `true`, so that the launcher can immediately update the user ID and user properties.

### Adding an Account
If a user wants to add an account, the launcher needs to ask the user for the authentication server to use, the user's account, and password. This authentication server can be pre-configured, selected by the user from a list of authentication servers, or configured by the user on the spot ([see above](#authentication-server-configuration)).

Afterward, the launcher performs the following operations:
1. Call the [Login Interface](yggdrasil-server-technical-specification.md#login) of the corresponding authentication server, including the account and password entered by the user.
2. If `selectedProfile` in the response is not empty, then login is successful. Use the information in the response to update the account attributes. The process ends.
3. If `availableProfiles` in the response is empty, then the user has no profiles; trigger an exception.
4. Prompt the user to select a profile from `availableProfiles`.
5. Call the [Refresh Interface](yggdrasil-server-technical-specification.md#refresh), with the token being the token returned from the login operation, and `selectedProfile` being the profile selected by the user in the previous step.
6. Login successful. Use the information in the refresh response to update the account attributes.

### Verifying Credential Validity
Before using credentials (e.g., before launching the game), the launcher needs to verify their validity. If the credentials have expired, the user needs to log in again. The steps to verify credential validity are as follows:
1. Call the [Validate Token Interface](yggdrasil-server-technical-specification.md#validate-token), including the account's accessToken and clientToken.
2. If the request succeeds, the current credentials are valid. The process ends. Otherwise, continue.
3. Call the [Refresh Interface](yggdrasil-server-technical-specification.md#refresh), including the account's accessToken and clientToken.
4. If the request succeeds, use the information in the refresh response to update the account attributes. The process ends. Otherwise, continue.
5. The launcher asks the user to re-enter the password.
6. Call the [Login Interface](yggdrasil-server-technical-specification.md#login), including the user's account and the password entered in the previous step.
7. If `selectedProfile` in the login response is not empty, then:
    1. If the `uuid` in `selectedProfile` is the same as the UUID of the profile corresponding to the account, use the information in the login response to update the user attributes. The process ends.
    2. Trigger an exception (the original account's profile is no longer available).
8. Find the profile with the same UUID as the account's corresponding profile from `availableProfiles`. If none exists, trigger an exception (the original account's profile is no longer available).
9. Call the [Refresh Interface](yggdrasil-server-technical-specification.md#refresh), with the token being the token returned from the login operation, and `selectedProfile` being the profile found in the previous step.
10. Login successful. Use the information in the refresh response to update the account attributes.

### Displaying Account Information
When displaying an account, in addition to the name of the profile corresponding to the account, the launcher should also display the authentication server to which the account belongs ([see above](#server-name-display)), to prevent users from confusing profiles with the same name on different authentication servers.

If the launcher wants to display the profile skin, it can call the [Query Profile Attributes Interface](yggdrasil-server-technical-specification.md#query-profile-attributes) to obtain profile attributes, which contain the [profile's skin information](yggdrasil-server-technical-specification.md#serialization-of-profile-information).

## Launching the Game
Before launching the game, the launcher needs to perform the following tasks:
1. (If needed) [Download authlib-injector](#downloading-authlib-injector)
2. [Verify Credential Validity](#verifying-credential-validity)
3. [Configuration Prefetch](#configuration-prefetch)
4. [Add Launch Arguments](#adding-launch-arguments)

Steps 1, 2, and 3 can be executed in parallel to improve launch speed.

### Downloading authlib-injector
The launcher can include authlib-injector.jar itself, or it can download one (and cache it) before launching the game. This project provides [an API](get-authlib-injector.md#download-api) for downloading authlib-injector.

If your users are primarily in mainland China, we recommend downloading from the [BMCLAPI mirror](get-authlib-injector.md#bmclapi-mirror).

### Configuration Prefetch
Before launching, the launcher needs to send a GET request to the API address to obtain API metadata. This metadata will be passed to the game at launch time, so that authlib-injector does not need to directly request the authentication server, thereby improving launch speed and preventing game crashes at startup due to network failures.

### Adding Launch Arguments
#### Configuring authlib-injector
The launcher needs to add the following JVM arguments (which should be added before the main class argument):
1. javaagent argument:
    ```
    -javaagent:{path to authlib-injector.jar}={authentication server API address}
    ```
2. Configuration prefetch:
    ```
    -Dauthlibinjector.yggdrasil.prefetched={Base64 encoded API metadata}
    ```

The following uses example.yggdrasil.yushi.moe as an example:
 * authlib-injector.jar is located at `/home/user/.launcher/authlib-injector.jar`.
 * The authentication server API address is `https://example.yggdrasil.yushi.moe/`.
 * Send a GET request to `https://example.yggdrasil.yushi.moe/` to obtain API metadata:
    ```
    {"skinDomains":["yushi.moe"],"signaturePublickey":... (omitted)
    ```
    Base64 encode the above response to get:
    ```
    eyJza2luRG9tYWluc... (omitted)
    ```
 * Therefore, the JVM arguments to add are:
    ```
    -javaagent:/home/user/.launcher/authlib-injector.jar=https://example.yggdrasil.yushi.moe/
    -Dauthlibinjector.yggdrasil.prefetched=eyJza2luRG9tYWluc... (omitted)
    ```

#### Replacing Parameter Templates
The game version JSON file (`versions/<version>/<version>.json`) specifies the arguments used by the launcher when launching the game. Some authentication-related parameter templates should be replaced according to the following table:

| Parameter Template | Replace With |
| --- | --- |
| ${auth_access_token} | Account's accessToken |
| ${auth_session} | Account's accessToken |
| ${auth_player_name} | Profile name |
| ${auth_uuid} | Profile UUID (Unhyphenated) |
| ${user_type} | `mojang` |
| ${user_properties} | User properties (JSON format) (If the launcher does not support this, replace with `{}`) |
