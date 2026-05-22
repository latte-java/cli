# `LoginCommandTest` End-to-End Test Design

**Date:** 2026-05-22
**Status:** Approved (pending spec review)

## Summary

Add `LoginCommandTest`, a full end-to-end test that runs the real `LoginCommand.run()` against the local FusionAuth Docker instance. The test injects a headless `Browser` that performs the FusionAuth login over HTTP (instead of opening a window), so the entire OAuth Authorization Code + PKCE flow — authorize, login, redirect capture, token exchange, and token storage — runs against real FusionAuth.

## Goals

- Exercise `LoginCommand` end to end against real FusionAuth: authorize URL, login, loopback redirect, PKCE token exchange, token storage, and the success message.
- Run as part of `latte test` (group `"unit"`), consistent with the existing `S3ProcessTest` Docker-backed test.
- Never open a real browser window and never write to the user's real `~/.config/latte/config.properties`.

## Non-goals

- Managing the FusionAuth container lifecycle (assumed already running; see Docker handling).
- Testing the production no-arg `LoginCommand` browser/config behavior (covered by prod defaults, not asserted here).
- Skipping gracefully when FusionAuth is down — the test throws with instructions, matching `S3ProcessTest`.

## Decisions

| Decision | Choice |
| --- | --- |
| Drive login | Test acts as a headless browser over HTTP (CookieManager + form scrape) |
| Test seams | Package-private `LoginCommand(Browser, Path)` constructor (prod no-arg keeps defaults) |
| Docker handling | Assume running; `@BeforeClass` pings `:9011`, throws with `docker compose up` instructions if absent |
| Test group | `"unit"` (runs with `latte test`) |
| Test user | `test@lattejava.org` / `password` (pre-registered against the CLI app in kickstart) |

## Flow

```
LoginCommand.run(config[args=["http://localhost:9011"]], output, project=null)
  ├─ AuthConfiguration, PKCE, state            (real, inside run())
  ├─ LoopbackServer.start() on :8888           (real)
  ├─ browser.open(authorizeURL, output)  ──►   TEST Browser impl:
  │      CookieManager-backed HttpClient, followRedirects(NORMAL):
  │        GET authorizeURL              → FusionAuth login page (200, session cookie)
  │        scrape <form action> + hidden <input> fields (incl. anti-CSRF)
  │        POST loginId=test@lattejava.org & password=password & hidden fields
  │        FusionAuth 302 → http://localhost:8888/callback?code&state
  │        HttpClient auto-follows the redirect → loopback handler completes the code future
  ├─ awaitCode()  → returns immediately (future already completed above)
  ├─ OAuthClient.exchangeCode(...)             (real PKCE token exchange vs FusionAuth)
  ├─ CredentialStore(tempConfigFile).store()   (temp file, not ~/.config)
  └─ prints "Logged in as [test@lattejava.org]"
```

Because the test `Browser` follows the final 302 to the loopback itself, the loopback's
code future is completed before `browser.open()` returns; `awaitCode()` then returns
immediately. The loopback `HttpServer` runs the callback handler on its own thread, so
there is no deadlock and no need for extra test threads or sleeps.

## Components

### `Browser` (new, `src/main/java/org/lattejava/cli/auth/Browser.java`)

A functional interface:

```java
@FunctionalInterface
public interface Browser {
  void open(String url, Output output);
}
```

`Browsers.open(String, Output)` already matches this signature, so the production default
is the method reference `Browsers::open`.

### `LoginCommand` refactor (`src/main/java/org/lattejava/cli/command/LoginCommand.java`)

- Add two fields: `Browser browser` and `Path configFile`.
- Public no-arg constructor (production): `this(Browsers::open, LattePaths.get().configDir().resolve("config.properties"))`.
- Package-private constructor (tests): `LoginCommand(Browser browser, Path configFile)`.
- `run()` calls `browser.open(...)` instead of `Browsers.open(...)`, and `new CredentialStore(configFile)` instead of building the path inline.

This follows the existing `InitCommand(Scanner)` package-private test-constructor pattern.

### Headless-login `Browser` (test helper)

A `Browser` implementation in the test package (a small named helper class or a factory
method in the test) that, given the authorize URL:

1. Builds an `HttpClient` with a `CookieManager` and `followRedirects(NORMAL)`.
2. `GET`s the authorize URL → FusionAuth's default hosted login page (200), capturing the session cookie.
3. Regex-scrapes the login `<form>`'s `action` attribute and all `<input type="hidden">` `name`/`value` pairs (including the anti-CSRF token if present). No HTML library is on the classpath (json-simple only), so simple regex parsing is used.
4. `POST`s the form: the scraped hidden fields plus `loginId` and `password`, form-url-encoded.
5. FusionAuth responds `302` to `http://localhost:8888/callback?code=...&state=...`; the client auto-follows it, delivering the code to the loopback server.

The form-scraping is the one piece coupled to FusionAuth's login HTML; it is isolated to
this helper and will be verified against the running container during implementation.

## Docker handling

`LoginCommandTest` is annotated `@Test(groups = "unit")`. `@BeforeClass` sends a short-timeout
`GET http://localhost:9011/api/status`; if it does not return 200, it throws a
`RuntimeException` whose message explains how to start FusionAuth
(`cd src/test/fusionauth && docker compose up -d`), mirroring `S3ProcessTest`.

## Test

`loginStoresTokensForOrdinaryUser`:

1. Create a temp config file path (e.g. `Files.createTempDirectory(...).resolve("config.properties")`).
2. Build a `RuntimeConfiguration` with `args = ["http://localhost:9011"]`.
3. Construct `new LoginCommand(headlessBrowser, tempConfigFile)`, where `headlessBrowser`
   logs in as `test@lattejava.org` / `password`.
4. Call `command.run(runtimeConfiguration, output, null)`.
5. Assert:
   - the temp `config.properties` contains `latte.auth.accessToken` and `latte.auth.refreshToken`;
   - decoding the access-token JWT's `email` claim (via `JWTs.claim`) yields `test@lattejava.org`.

## Error handling / risks

- Port `8888` must be free during the test (the loopback binds it); FusionAuth must be reachable on `:9011`.
- If FusionAuth's login-form HTML differs from expectations, the scrape in the headless `Browser` may need adjustment; this is localized to the helper and verified against the live container during implementation.
- The test depends on the kickstart having `test@lattejava.org` registered against the CLI application with `requireRegistration: true` (present in the current kickstart).
