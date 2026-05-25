# `latte()` Publish Process Design

**Date:** 2026-05-24
**Status:** Approved

## Summary

Add a new `Process` implementation, `LatteProcess`, that publishes artifacts to the Latte
repository through the `app`'s authenticated publish API instead of requiring raw S3/R2
credentials. It plugs into the existing `publish { … }` workflow exactly like `s3(…)`, so the
release flow (`DefaultDependencyService.publish` → `PublishWorkflow.publish` → `Process.publish`)
drives it without change. Authentication uses the OAuth tokens that `latte login` already stores
in `~/.config/latte/config.properties`.

## The API contract (from `app`)

`POST {baseURL}/api/v1/publish/{group}`

- **Request headers:** `Authorization: Bearer <accessToken>`, `X-Refresh-Token: <refreshToken>`
  (so the server can refresh server-side), `Content-Type: application/json`.
- **Request body:** `{"fileName": "<objectKey>"}` where the key must start with
  `group.toLowerCase().replace('.', '/') + "/"` (the server's `PublishValidator`).
- **Success (200):** `{"url": "<presigned PUT URL>"}`. When the server refreshed the access token
  mid-request it returns the new token in the `X-Access-Token` response header and, if rotated, a
  new `X-Refresh-Token`. The presigned URL signs `host` only (`UNSIGNED-PAYLOAD`), so a plain `PUT`
  of the bytes uploads the artifact.
- **Errors** (rendered by the app's `APIExceptionHandler` + the `web` OIDC middleware):
  - `400` validation → an `Errors` object (`{"fieldErrors": {...}, "generalErrors": [...]}`); codes
    `[blank]fileName`, `[outsideNamespace]fileName`, `[uncleanKey]fileName`.
  - `400` bad body → `{"error": "BadRequestException", "message": ...}`.
  - `401` `UnauthenticatedException` → token missing/invalid and not refreshable.
  - `403` `ForbiddenException` → not an active OWNER/CONTRIBUTOR of a VERIFIED owning group.
  - `500` `InternalError` → the server could not presign.
  - `503` `ServiceUnavailableException` → the IdP could not be reached.

## Components

### `PublishAPIClient` (`dep/workflow/process/`)

Pure HTTP against the publish API. No file I/O. Constructed with the base API URL (trailing slash
stripped) and an `HttpClient` (injected for testing).

- `PresignResponse requestPresignedURL(String group, String key, Tokens tokens)` — POSTs the
  request, reads `X-Access-Token`/`X-Refresh-Token` from the response, maps any non-200 to a
  `ProcessFailureException` with a status-specific message, and parses `{"url": ...}`.
  - `record PresignResponse(String url, Tokens refreshedTokens)` — `refreshedTokens` is non-null
    only when the server returned an `X-Access-Token`.
- `void upload(String presignedURL, byte[] body)` — plain `PUT` of the bytes; throws on non-200.
- `describeError(int status, String group, String body)` — builds the per-status message, reading
  the best available text from either the `Errors` shape or the `{error, message}` shape.

### `LatteProcess` (`dep/workflow/process/`) implements `Process`

Orchestrates a single item publish:

1. Derive the object key: `group.replace('.', '/') + "/" + project + "/" + version + "/" + item`
   (same layout as `S3Process`). Group for the URL path is `item.group`.
2. `CredentialStore.load()`; if there is no access token, fail with a "run `latte login`" message.
3. `requestPresignedURL(group, key, tokens)`.
4. If `refreshedTokens` is present, persist them via `CredentialStore.store(...)`.
5. `upload(url, bytes)`.
6. Log `Published [item] to the Latte repository [key]`.

`fetch(...)` returns `null` — this process is publish-only; fetching the public repository stays
with the existing `S3Process`/`URLProcess`. Default base URL is `https://api.lattejava.org`.

Constructors: a public `LatteProcess(Output, String apiURL)` (production — default config file +
real `HttpClient`) and a package-private `LatteProcess(Output, String apiURL, Path configFile,
PublishAPIClient client)` (tests).

### `CredentialStore.load()` (`cli/auth/`)

New method returning `Tokens` read from the config file (null fields when absent). Reusing
`CredentialStore`/`Tokens` keeps the `latte.auth.*` keys and file permissions as the single source
of truth. The `dep` → `cli.auth` dependency is intentional: both concern authentication.

### `ProcessDelegate.latte(...)` (`cli/parser/groovy/WorkflowDelegate.java`)

```groovy
publish {
  latte()                                  // defaults to https://api.lattejava.org
  latte(apiURL: "http://localhost:8080")   // override for local testing
}
```

## Error → message mapping

| Status | CLI message |
| --- | --- |
| 401 | Your Latte login has expired or is invalid. Run [latte login] and try publishing again. |
| 403 | You are not authorized to publish to the group [X]. The group must be verified and you must be an active owner or contributor. |
| 400 | The Latte repository rejected the publish request: \<field/general messages\>. |
| 404 | The Latte publish API was not found at [apiURL]. Check the configured [apiURL]. |
| 500 | The Latte repository could not generate an upload URL (server error). Please try again later. |
| 503 | The Latte identity provider is temporarily unavailable. Please try again shortly. |
| other / PUT failure | Publishing to the Latte repository failed with HTTP [status]: \<body\>. |

## Testing (all local, no Docker — mirrors `OAuthClientTest`/`LoopbackServerTest`)

- **`PublishAPIClientTest`** — stub `HttpServer`: asserts path/headers/body on 200; parses `url` +
  refreshed tokens; one case per error status verifying the mapped message; `upload()` receives the
  exact bytes and fails on non-200.
- **`LatteProcessTest`** — temp `config.properties` with tokens + two stub servers (API returns a
  presigned URL pointing at the second stub): verifies end-to-end publish, that rotated tokens are
  written back to the temp file, and that missing tokens fail with the `latte login` message.
- **`CredentialStoreTest`** — add a `load()` round-trip.
- **`GroovyProjectFileParserTest`** — a `latte-workflow.latte` fixture + assertion that a
  `LatteProcess` is configured (mirrors the existing `s3-workflow.latte` test).
- Run the full `latte test` afterward; adding source files can break the hardcoded file-count
  assertions in the Jar/Tar/Zip/FileSet tests, which will be fixed if they trip.

## Out of scope

No changes to any `project.latte` `publish{}` block — wiring `latte()` into the actual release is a
separate step.
