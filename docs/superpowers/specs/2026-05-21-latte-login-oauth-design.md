# `latte login` — OAuth Authentication Design

**Date:** 2026-05-21
**Status:** Approved (pending spec review)

## Summary

Add a new global command `latte login` that authenticates a user against the Latte
FusionAuth IdP using the OAuth 2.0 Authorization Code flow with PKCE (RFC 7636) and a
loopback redirect (RFC 8252), acting as a **public client** (no client secret). The
resulting access and refresh tokens are stored in `~/.config/latte/config.properties`
for later use when publishing artifacts to the Latte repository.

## Goals

- Let a user log into the Latte IdP from the command line with a single command.
- Capture the OAuth redirect locally and exchange the authorization code for tokens.
- Persist the access and refresh tokens to the existing global config file.
- Work zero-config against production, and against the local FusionAuth Docker setup
  by passing the local issuer URL as an argument.

## Non-goals

- Token refresh / automatic re-authentication (a later feature; consumes the stored
  refresh token).
- A `logout` command.
- Using the tokens to publish artifacts (a separate, later feature).
- JWKS signature verification of the access token (unnecessary — the token is received
  directly from the IdP over TLS).

## Decisions

| Decision          | Choice                                                                     |
|-------------------|----------------------------------------------------------------------------|
| OAuth flow        | Authorization Code + PKCE, loopback redirect                               |
| Client type       | Public client (no secret), PKCE required                                   |
| Loopback redirect | Fixed port `8888`, `http://localhost:8888/callback`                        |
| IdP configuration | Issuer from optional positional argument; client ID hardcoded constant     |
| Prod issuer       | `https://auth.lattejava.org` (default when no argument is given)            |
| Client ID         | Single fixed UUID, hardcoded as the default and used in kickstart          |
| Scopes            | `openid offline_access`                                                    |
| Token storage     | `latte.auth.accessToken`, `latte.auth.refreshToken` in `config.properties` |
| Success identity  | Decode access-token JWT payload (no signature check), read `email` claim   |

## Usage

```
latte login                          # production: https://auth.lattejava.org
latte login http://localhost:9011    # local FusionAuth Docker setup
```

The single optional positional argument is the FusionAuth issuer base URL. When omitted
it defaults to `https://auth.lattejava.org`. The client ID is always the hardcoded
constant.

## Flow

```
latte login [issuer]
  ├─ resolve issuer (argument or default https://auth.lattejava.org)
  ├─ generate PKCE code_verifier (32 random bytes, base64url) + code_challenge (S256)
  ├─ generate random `state`
  ├─ start loopback HTTP server on http://localhost:8888/callback
  ├─ open browser → {issuer}/oauth2/authorize?
  │        client_id&redirect_uri=http://localhost:8888/callback&
  │        response_type=code&scope=openid offline_access&
  │        code_challenge&code_challenge_method=S256&state
  ├─ user authenticates in the browser → FusionAuth redirects to /callback?code&state
  ├─ loopback server validates `state`, captures `code`, renders a
  │        "Login complete — you can close this tab" page
  ├─ POST {issuer}/oauth2/token
  │        grant_type=authorization_code, code, redirect_uri, client_id, code_verifier
  │        → { access_token, refresh_token }
  ├─ decode access-token JWT payload (base64url, no signature check) → email claim
  ├─ write latte.auth.accessToken + latte.auth.refreshToken to config.properties (0600)
  └─ print "Logged in as <email>"
```

The authorize and token endpoints are FusionAuth's well-known paths relative to the
issuer (`/oauth2/authorize`, `/oauth2/token`); no OIDC discovery request is needed.

## Components

New package `org.lattejava.cli.auth` keeps OAuth internals isolated from the command
and independently testable. All components use only the JDK
(`java.net.http.HttpClient`, `com.sun.net.httpserver.HttpServer`) and the existing
`json-simple` dependency. The CLI is a classpath application (no `module-info.java`),
so no module declarations are required.

- **`LoginCommand implements Command`** (`org.lattejava.cli.command`) — orchestrates the
  steps and prints output. Reads the optional issuer from the first positional argument
  (`configuration.args`), defaulting to `https://auth.lattejava.org`. Registered as
  `"login"` in `DefaultRunner.COMMANDS`; a help line is added to `Main.printHelp`. Does
  not require a project file (`project` may be null).
- **`PKCE`** — generates the `code_verifier` (32 random bytes from `SecureRandom`,
  base64url-encoded, no padding) and the `code_challenge`
  (`BASE64URL(SHA-256(code_verifier))`). Pure and unit-testable.
- **`LoopbackServer`** — wraps `HttpServer` bound to `localhost:8888`, handles a single
  `/callback` request, validates `state`, returns the authorization code (or an OAuth
  `error`), and renders an HTML completion page. Fails fast with a clear message if the
  port is in use. Has a bounded wait (~2 minutes) for the callback.
- **`OAuthClient`** — builds the authorize URL, performs the token `POST` via
  `HttpClient` (form-encoded body), parses the JSON response with json-simple, and
  returns a `Tokens` record (`accessToken`, `refreshToken`).
- **`JWTs`** — base64url-decodes the payload segment of a JWT and reads a string claim
  (`email`). No signature verification.
- **`CredentialStore`** — loads the existing `config.properties` into a `Properties`
  object, sets `latte.auth.accessToken` and `latte.auth.refreshToken`, and writes the
  file back with `0600` permissions, preserving other properties. (`Properties.store`
  drops comments and ordering — acceptable for this generated config file.)
- **`AuthConfiguration`** — holds the resolved issuer (from the command argument or the
  `https://auth.lattejava.org` default) and the hardcoded client-ID UUID constant, and
  derives the authorize/token endpoint URLs and the fixed `http://localhost:8888/callback`
  redirect URI. No config-file reading. Also provides cross-platform browser opening
  (`java.awt.Desktop` when available, falling back to `open`/`xdg-open`), with a printed
  URL fallback when no browser can be launched.

## Configuration keys (`~/.config/latte/config.properties`)

The config file is only ever *written* by this command — never read for OAuth settings
(issuer comes from the argument, client ID is a hardcoded constant).

| Key                       | Meaning            |
|---------------------------|--------------------|
| `latte.auth.accessToken`  | Written on success |
| `latte.auth.refreshToken` | Written on success |

## FusionAuth kickstart changes

File: `src/test/fusionauth/kickstart/kickstart.json`. All existing requests are left
untouched. Add:

1. A new variable `cliApplicationId` set to the fixed CLI client-ID UUID (the same value
   hardcoded in `AuthConfiguration`).
2. A new `POST /api/application/#{cliApplicationId}` request named **"Latte CLI"**:
   - `oauthConfiguration.authorizedRedirectURLs = ["http://localhost:8888/callback"]`
   - `oauthConfiguration.clientAuthenticationPolicy = "NotRequired"` (public client)
   - `oauthConfiguration.proofKeyForCodeExchangePolicy = "Required"` (enforce PKCE)
   - `oauthConfiguration.enabledGrants = ["authorization_code", "refresh_token"]`
   - `oauthConfiguration.generateRefreshTokens = true`
   - `oauthConfiguration.requireRegistration = true`
   - `jwtConfiguration` → `accessTokenKeyId` / `idTokenKeyId` = `#{asymmetricKeyId}`
   - `lambdaConfiguration.accessTokenPopulateId = #{accessTokenPopulateLambdaId}` (reuses
     the existing JWTPopulate lambda so the access token carries `email` and
     `preferred_username`)
   - `registrationConfiguration` (advanced) → `formId = #{registrationFormId}`
   - `formConfiguration.selfServiceFormId = #{selfServiceFormId}`
3. Register the existing **admin** (`#{adminUserId}`) and **ordinary** (`#{ordinaryUserId}`)
   users against `#{cliApplicationId}` so `latte login` works out of the box locally.

## Error handling

All failures surface as `RuntimeFailureException` with runtime values wrapped in square
brackets (per project convention):

- Port `8888` already in use → instruct the user to free the port.
- No browser can be launched → print the authorize URL for manual paste.
- `state` mismatch on the callback → abort (possible CSRF / stale attempt).
- OAuth `error` parameter on the callback (e.g. user denied consent) → report it.
- Token endpoint returns non-200 → report status and any `error_description`.
- Callback not received within the timeout (~2 minutes) → abort with guidance.

## Testing

- **Unit**
  - `PKCE`: `code_challenge` equals `BASE64URL(SHA-256(code_verifier))`; verifier is
    high-entropy and URL-safe.
  - `JWTs`: decodes a known token and extracts the `email` claim; handles malformed
    input.
  - `CredentialStore`: merging preserves pre-existing properties and sets the two new
    keys; file is written with `0600`.
  - `AuthConfiguration`: issuer defaults to `https://auth.lattejava.org` when no argument
    is given and uses the argument when present; derives correct authorize/token/redirect
    URLs.
- **Integration**
  - `OAuthClient` / `LoopbackServer` against a stub `HttpServer` (same pattern as
    `NetToolsTest` / `BaseUnitTest`): simulate the redirect to `/callback` and a canned
    token JSON response; assert tokens are parsed and `state` is validated. No live
    FusionAuth needed in the suite.

## Out-of-suite manual verification

With the FusionAuth Docker compose running, run `latte login http://localhost:9011`,
authenticate as the ordinary user in the browser, and confirm the success message and
that the two token keys appear in `config.properties`.
