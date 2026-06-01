# Publish Readiness Pre-Check (before Git tag creation)

**Date:** 2026-06-01
**Status:** Approved design — ready for implementation plan

## Problem

The `release-git` plugin creates and **pushes** a Git tag (`tag(git)`) *before* it
publishes artifacts (`publish()`). If publishing later fails — most importantly
because the user is not logged in or lacks permission to publish to the project's
group on the Latte repository — the release aborts but the tag has already been
created and pushed to the remote. These orphan tags must then be cleaned up by hand.

We want to detect "this release cannot be published" *before* the tag is created, so
a doomed release fails early and leaves no tags behind.

## Goal

Add a publish-readiness pre-check to the publish process system and run it in the
`release-git` plugin before the Git tag is created. The primary check is for
`LatteProcess`: confirm the stored OAuth tokens are valid and that the user is
permitted to publish to the project's group, using a new authenticated `HEAD` call
to the publish API.

## Non-Goals

- No change to the actual fetch/publish behavior of any process.
- No retry/refresh policy changes beyond reusing the existing token-rotation
  handling already present for `publish()`.
- No collection/aggregation of every failure reason — the release fails on the first
  not-ready process (see Decisions).

## Design

### 1. `PublishReadiness` record — `org.lattejava.dep.workflow.process`

A small value type carrying the verdict and a human-readable reason.

```java
public record PublishReadiness(boolean ready, String message) {
  public static PublishReadiness ready() {
    return new PublishReadiness(true, null);
  }

  public static PublishReadiness notReady(String message) {
    return new PublishReadiness(false, message);
  }
}
```

`message` is `null` when `ready` is `true`.

### 2. `Process` interface — new default method

```java
default PublishReadiness verifyPublishReadiness(Project project) {
  return PublishReadiness.ready();
}
```

- Default returns **ready**, so `CacheProcess`, `S3Process`, `URLProcess`, and
  `MavenProcess` inherit a no-op and only `LatteProcess` overrides it.
- The parameter is `org.lattejava.cli.domain.Project`. The check reads
  `project.group` to scope the permission check.

**Layering note:** `Process` lives in `dep.workflow.process` and `Project` lives in
`cli.domain`. This adds a `dep -> cli.domain` import. The CLI main source has no
`module-info.java` (it builds on the classpath), so there is no module boundary and
this compiles; there is existing precedent in this package (`LatteProcess` /
`PublishAPIClient` already import `cli.auth`). Accepted as-is per design decision.

### 3. `LatteProcess.verifyPublishReadiness(Project)`

```java
@Override
public PublishReadiness verifyPublishReadiness(Project project) {
  CredentialStore credentialStore = new CredentialStore(configFile);
  Tokens tokens = credentialStore.load();
  if (tokens.accessToken() == null) {
    return PublishReadiness.notReady(
        "You are not logged in to the Latte repository. Run [latte login] before releasing.");
  }

  PublishAPIClient.PermissionResponse response = client.verifyPublishPermission(project.group, tokens);
  if (response.refreshedTokens() != null) {
    credentialStore.store(response.refreshedTokens());
  }

  return response.readiness();
}
```

Mirrors the token-load / refresh-persist pattern already used in `publish()`.

### 4. `PublishAPIClient.verifyPublishPermission(String group, Tokens tokens)`

- Issues `HEAD {apiURL}/api/v1/publish/{group}` with header
  `Authorization: Bearer <accessToken>` and, when present, `X-Refresh-Token`.
- `200` → `PublishReadiness.ready()`.
- Any non-200 → `PublishReadiness.notReady(describeError(status, group, ""))`. The
  existing private `describeError` mapping is reused; `HEAD` responses carry no body,
  which `describeError`/`messages`/`serverDetail` already tolerate (empty body).
- Captures rotated tokens from `X-Access-Token` / `X-Refresh-Token` response headers
  (same logic as `requestPresignedURL`).
- Network/interruption failures throw `ProcessFailureException`, consistent with the
  other client methods.

`describeError` is shared by both `requestPresignedURL` and `verifyPublishPermission`.
Its 401 message is reworded once to read naturally in both publish and release
contexts (e.g. "Your Latte login has expired or is invalid. Run [latte login] and try
again." — dropping the publish-specific "and try publishing again"). Because the
mapping is shared, this single wording applies to both paths; no per-path branching.

Returns a new record mirroring `PresignResponse`:

```java
public record PermissionResponse(PublishReadiness readiness, Tokens refreshedTokens) {
}
```

### 5. `release-git` plugin — run the check before tagging

In `ReleaseGitPlugin.release()`, insert a new step between
`checkPluginsForIntegrationVersions()` and `tag(git)`:

```groovy
void release() {
  if (!project.publishWorkflow) {
    fail("You must specify a publishWorkflow in the project definition of your project.latte file.")
  }

  Git git = new Git(project.directory)
  updateGitAndCheckWorkingCopy(git)
  checkIfTagIsAvailable(git)
  checkDependenciesForIntegrationVersions()
  checkPluginsForIntegrationVersions()
  verifyPublishReadiness()            // NEW — before any tag is created
  tag(git)
  publish()
}

private void verifyPublishReadiness() {
  output.infoln("Verifying publish readiness")

  project.publishWorkflow.getProcesses().each({ process ->
    PublishReadiness readiness = process.verifyPublishReadiness(project)
    if (!readiness.ready()) {
      fail("Unable to release. ${readiness.message()}")
    }
  })
}
```

- The plugin iterates `publishWorkflow.getProcesses()` directly. No changes to
  `PublishWorkflow` or `Workflow`.
- `fail(...)` throws, so the release stops on the **first** not-ready process, before
  any tag is created or pushed.

## Decisions (resolved during brainstorming)

1. **Parameter type:** `Project`, not `ResolvableItem`/`FetchResult`/`String`. The
   check runs **once per release**, not once per publication.
2. **Contract:** returns a `PublishReadiness` result; the caller decides policy
   (chosen over throwing or a bare boolean).
3. **Aggregation:** the `release-git` plugin iterates the processes itself; no
   `Workflow`-level aggregator is added.
4. **HEAD endpoint:** `HEAD /api/v1/publish/{group}` with token refresh support;
   status mapping reuses `describeError` (200 ok; 401 token; 403 group permission;
   404 API; 503 IdP; etc.).
5. **Failure handling:** fail on the first not-ready process (simple, `fail()` throws).
6. **Naming:** `verifyPublishReadiness` / `PublishReadiness` /
   `verifyPublishPermission` / `PermissionResponse`.

## Testing

Existing tests use TestNG with a stub `com.sun.net.httpserver.HttpServer` and a
temporary config file for stored tokens (see `LatteProcessTest`,
`PublishAPIClientTest`). New tests follow the same patterns.

- **`PublishAPIClientTest`**
  - `verifyPublishPermission` sends `HEAD` with the `Authorization` bearer header.
  - `200` → `readiness.ready() == true`.
  - `403` → not ready, message names the group.
  - Rotated `X-Access-Token`/`X-Refresh-Token` captured on `PermissionResponse`.
- **`LatteProcessTest`**
  - Not logged in (no access token) → not ready, message mentions `latte login`.
  - HEAD `200` → ready.
  - HEAD `401`/`403` → not ready.
  - Refreshed tokens persisted to the config file.
- **`ReleaseGitPluginTest`**
  - When a process reports not ready, `release()` fails at the readiness step and
    **no tag is created** (assert the tag/push commands were not run).
  - When all processes report ready, `release()` proceeds through `tag()`/`publish()`.

## Conventions

- SPDX header `Copyright (c) 2026 The Latte Project` / `SPDX-License-Identifier: MIT`
  on new files; bump existing modified files to a year range if needed.
- Acronyms fully uppercased (`HEAD`, `URL`, `API`), error/runtime values wrapped in
  `[brackets]`, fields/methods alphabetized within visibility groups.
- Match the Groovy `groovy` module version in `plugins/release-git/project.latte` to
  the project's groovy dependency when touching the plugin.

## Files Touched

New:
- `src/main/java/org/lattejava/dep/workflow/process/PublishReadiness.java`

Modified:
- `src/main/java/org/lattejava/dep/workflow/process/Process.java`
- `src/main/java/org/lattejava/dep/workflow/process/LatteProcess.java`
- `src/main/java/org/lattejava/dep/workflow/process/PublishAPIClient.java`
- `plugins/release-git/src/main/groovy/org/lattejava/plugin/release/ReleaseGitPlugin.groovy`

Tests:
- `src/test/java/org/lattejava/dep/workflow/process/PublishAPIClientTest.java`
- `src/test/java/org/lattejava/dep/workflow/process/LatteProcessTest.java`
- `plugins/release-git/src/test/groovy/org/lattejava/plugin/release/ReleaseGitPluginTest.groovy`
