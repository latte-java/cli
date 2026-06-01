# Publish Readiness Pre-Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a publish-readiness pre-check to the publish `Process` system and run it in the `release-git` plugin *before* the Git tag is created, so a release that cannot be published fails early and leaves no orphan tags.

**Architecture:** A new `PublishReadiness` result type and a default `Process.verifyPublishReadiness(Project)` interface method (no-op → ready). `LatteProcess` overrides it to confirm login and call a new authenticated `HEAD /api/v1/publish/{group}` on `PublishAPIClient`. The `release-git` plugin iterates `publishWorkflow.getProcesses()` and fails before tagging if any process is not ready.

**Tech Stack:** Java 25, TestNG + `com.sun.net.httpserver.HttpServer` stubs, Groovy (release-git plugin), Latte build (`latte test` / `latte int`).

---

## Conventions (apply to every task)

- SPDX header on every new file (no Javadoc form, no "All Rights Reserved"):
  ```java
  /*
   * Copyright (c) 2026 The Latte Project
   * SPDX-License-Identifier: MIT
   */
  ```
- Acronyms fully uppercase (`HEAD`, `URL`, `API`). Runtime values in error/log messages wrapped in `[brackets]`. Methods/fields alphabetized within visibility groups.
- Run a single Java test class with: `latte test --test=ClassName`
- The `release-git` plugin tests exec `git` and run from a plugin subdir. When invoking via the Bash tool, set `dangerouslyDisableSandbox: true` or the exec'd `git` calls fail with EPERM.

---

## File Structure

New:
- `src/main/java/org/lattejava/dep/workflow/process/PublishReadiness.java` — readiness result value type.

Modified:
- `src/main/java/org/lattejava/dep/workflow/process/Process.java` — add default `verifyPublishReadiness`.
- `src/main/java/org/lattejava/dep/workflow/process/PublishAPIClient.java` — add `verifyPublishPermission` + `PermissionResponse`; reword 401 message.
- `src/main/java/org/lattejava/dep/workflow/process/LatteProcess.java` — override `verifyPublishReadiness`.
- `plugins/release-git/src/main/groovy/org/lattejava/plugin/release/ReleaseGitPlugin.groovy` — add `verifyPublishReadiness()` step before `tag(git)`.

Tests:
- `src/test/java/org/lattejava/dep/workflow/process/PublishReadinessTest.java` (new)
- `src/test/java/org/lattejava/dep/workflow/process/CacheProcessTest.java` (modify — default-ready test)
- `src/test/java/org/lattejava/dep/workflow/process/PublishAPIClientTest.java` (modify)
- `src/test/java/org/lattejava/dep/workflow/process/LatteProcessTest.java` (modify)
- `plugins/release-git/src/test/groovy/org/lattejava/plugin/release/ReleaseGitPluginTest.groovy` (modify)

---

## Task 1: `PublishReadiness` result type

**Files:**
- Create: `src/main/java/org/lattejava/dep/workflow/process/PublishReadiness.java`
- Test: `src/test/java/org/lattejava/dep/workflow/process/PublishReadinessTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/lattejava/dep/workflow/process/PublishReadinessTest.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the PublishReadiness result type and its factory methods.
 *
 * @author Brian Pontarelli
 */
public class PublishReadinessTest extends BaseUnitTest {
  @Test
  public void factories() {
    PublishReadiness ready = PublishReadiness.READY;
    assertTrue(ready.ready());
    assertNull(ready.message());

    PublishReadiness notReady = PublishReadiness.notReady("You cannot publish.");
    assertFalse(notReady.ready());
    assertEquals(notReady.message(), "You cannot publish.");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `latte test --test=PublishReadinessTest`
Expected: FAIL — compilation error, `PublishReadiness` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/org/lattejava/dep/workflow/process/PublishReadiness.java`:

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

/**
 * The result of verifying whether a {@link Process} can publish a project's artifacts. A not-ready result carries a
 * human-readable message explaining why publishing would fail, so a release can be aborted with a useful error before
 * any irreversible step.
 *
 * @param ready   Whether the process is able to publish.
 * @param message The reason publishing would fail, or {@code null} when ready.
 *
 * @author Brian Pontarelli
 */
public record PublishReadiness(boolean ready, String message) {
  /** A ready result with no message. (A constant, not a {@code ready()} factory, since the record already
      auto-generates a {@code ready()} accessor for the boolean component.) */
  public static final PublishReadiness READY = new PublishReadiness(true, null);

  /**
   * @param message The reason publishing would fail.
   * @return A not-ready result carrying the given message.
   */
  public static PublishReadiness notReady(String message) {
    return new PublishReadiness(false, message);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `latte test --test=PublishReadinessTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/dep/workflow/process/PublishReadiness.java src/test/java/org/lattejava/dep/workflow/process/PublishReadinessTest.java
git commit -m "feat: Add PublishReadiness result type for publish pre-checks"
```

---

## Task 2: `Process.verifyPublishReadiness` default method

**Files:**
- Modify: `src/main/java/org/lattejava/dep/workflow/process/Process.java`
- Test: `src/test/java/org/lattejava/dep/workflow/process/CacheProcessTest.java`

- [ ] **Step 1: Write the failing test**

Add this test method to `CacheProcessTest` (a process that inherits the default). Ensure these imports exist at the top of the file (add any that are missing): `import org.lattejava.cli.domain.Project;` and `import java.nio.file.Paths;`.

```java
  @Test
  public void verifyPublishReadinessDefaultsToReady() {
    CacheProcess process = new CacheProcess(output, null, null, null);
    Project project = new Project(Paths.get(""), output);
    project.group = "org.example";

    PublishReadiness readiness = process.verifyPublishReadiness(project);

    assertTrue(readiness.ready());
    assertNull(readiness.message());
  }
```

If `assertTrue`/`assertNull` are not already statically imported in the file, add:
```java
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `latte test --test=CacheProcessTest`
Expected: FAIL — compilation error, `verifyPublishReadiness` is not defined on `Process`/`CacheProcess`.

- [ ] **Step 3: Write minimal implementation**

In `src/main/java/org/lattejava/dep/workflow/process/Process.java`, add the import (alphabetical order — before `dep.domain.ResolvableItem`):

```java
import org.lattejava.cli.domain.Project;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
```

Then add this default method to the `Process` interface (place after the existing `publish` method):

```java
  /**
   * Verifies that this process is able to publish artifacts for the given project. Processes that publish to a remote
   * location should confirm the caller's credentials and permissions here, so a release can fail before any
   * irreversible step (such as creating a Git tag). The default implementation reports ready, which is correct for
   * processes that have no remote publish step (caches and fetch-only processes).
   *
   * @param project The project being released. Implementations typically use {@link Project#group} to scope the check.
   * @return A {@link PublishReadiness} describing whether this process can publish and, if not, why.
   */
  default PublishReadiness verifyPublishReadiness(Project project) {
    return PublishReadiness.READY;
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `latte test --test=CacheProcessTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/dep/workflow/process/Process.java src/test/java/org/lattejava/dep/workflow/process/CacheProcessTest.java
git commit -m "feat: Add default verifyPublishReadiness to the Process interface"
```

---

## Task 3: `PublishAPIClient.verifyPublishPermission` (HEAD)

**Files:**
- Modify: `src/main/java/org/lattejava/dep/workflow/process/PublishAPIClient.java`
- Test: `src/test/java/org/lattejava/dep/workflow/process/PublishAPIClientTest.java`

- [ ] **Step 1: Write the failing tests**

Add these three test methods to `PublishAPIClientTest` (it already imports `Tokens`, `HttpClient`, `HttpServer`, `InetSocketAddress`, `AtomicReference`, `StandardCharsets`, and has the private `respond` helper). Use unique ports not already used in the file (8910/8911 are taken) — use 8930/8931/8932.

```java
  @Test
  public void verifyPublishPermissionSendsHeadWithAuthorization() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8930), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      method.set(exchange.getRequestMethod());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      respond(exchange, 200, "");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8930", HttpClient.newHttpClient());
      PublishAPIClient.PermissionResponse response = client.verifyPublishPermission("org.example", new Tokens("AT", "RT"));

      assertEquals(method.get(), "HEAD");
      assertEquals(authorization.get(), "Bearer AT");
      assertTrue(response.readiness().ready());
      assertNull(response.readiness().message());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void verifyPublishPermissionReportsNotReadyOnForbidden() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8931), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> respond(exchange, 403, ""));
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8931", HttpClient.newHttpClient());
      PublishAPIClient.PermissionResponse response = client.verifyPublishPermission("org.example", new Tokens("AT", "RT"));

      assertFalse(response.readiness().ready());
      assertTrue(response.readiness().message().contains("org.example"), response.readiness().message());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void verifyPublishPermissionCapturesRefreshedTokens() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8932), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      exchange.getResponseHeaders().add("X-Access-Token", "new-AT");
      exchange.getResponseHeaders().add("X-Refresh-Token", "new-RT");
      respond(exchange, 200, "");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8932", HttpClient.newHttpClient());
      PublishAPIClient.PermissionResponse response = client.verifyPublishPermission("org.example", new Tokens("AT", "RT"));

      assertTrue(response.readiness().ready());
      assertEquals(response.refreshedTokens().accessToken(), "new-AT");
      assertEquals(response.refreshedTokens().refreshToken(), "new-RT");
    } finally {
      server.stop(0);
    }
  }
```

If `assertFalse` is not statically imported in the file, add `import static org.testng.Assert.assertFalse;`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `latte test --test=PublishAPIClientTest`
Expected: FAIL — compilation error, `verifyPublishPermission`/`PermissionResponse` do not exist.

- [ ] **Step 3: Write the implementation**

In `src/main/java/org/lattejava/dep/workflow/process/PublishAPIClient.java`:

(a) Reword the 401 case in the private `describeError` method so it reads naturally in both publish and release contexts (it is shared by both code paths):

```java
      case 401 -> "Your Latte login has expired or is invalid. Run [latte login] and try again.";
```

(b) Add the new public method. Place it after `upload` (alphabetical: `requestPresignedURL`, `upload`, `verifyPublishPermission`):

```java
  /**
   * Verifies that the caller is permitted to publish to the given group using an authenticated HEAD request. This does
   * not request a presigned URL or change anything server-side; it only confirms the access token is valid and the
   * caller is an authorized publisher for the group.
   *
   * @param group  The artifact group to check publish permission for.
   * @param tokens The caller's current access and refresh tokens.
   * @return The readiness verdict and any tokens the server rotated during the request.
   */
  public PermissionResponse verifyPublishPermission(String group, Tokens tokens) {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
                                             .uri(URI.create(apiURL + "/api/v1/publish/" + group))
                                             .header("Authorization", "Bearer " + tokens.accessToken())
                                             .header("Accept", "application/json")
                                             .timeout(Duration.ofSeconds(30))
                                             .method("HEAD", HttpRequest.BodyPublishers.noBody());
    if (tokens.refreshToken() != null) {
      builder.header("X-Refresh-Token", tokens.refreshToken());
    }

    HttpResponse<String> response;
    try {
      response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ProcessFailureException("The publish permission check to the Latte repository failed. Message was [" + e.getMessage() + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessFailureException("The publish permission check to the Latte repository was interrupted.");
    }

    Tokens refreshed = null;
    String newAccessToken = response.headers().firstValue("X-Access-Token").orElse(null);
    if (newAccessToken != null) {
      String newRefreshToken = response.headers().firstValue("X-Refresh-Token").orElse(tokens.refreshToken());
      refreshed = new Tokens(newAccessToken, newRefreshToken);
    }

    PublishReadiness readiness = response.statusCode() == 200
        ? PublishReadiness.READY
        : PublishReadiness.notReady(describeError(response.statusCode(), group, response.body()));

    return new PermissionResponse(readiness, refreshed);
  }
```

(c) Add the response record. Place it before `PresignResponse` (alphabetical among nested records):

```java
  /**
   * The result of a publish-permission check: the readiness verdict, and any tokens the server rotated during the
   * request.
   *
   * @param readiness       Whether the caller can publish to the group.
   * @param refreshedTokens The refreshed tokens, or {@code null} if the server did not refresh.
   */
  public record PermissionResponse(PublishReadiness readiness, Tokens refreshedTokens) {
  }
```

(Note: `java.net.*`, `java.net.http.*`, `java.time.*`, and `java.io.*` are already wildcard-imported in this file, so `HttpRequest`, `URI`, `Duration`, and `IOException` need no new imports.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `latte test --test=PublishAPIClientTest`
Expected: PASS (all existing tests plus the three new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/dep/workflow/process/PublishAPIClient.java src/test/java/org/lattejava/dep/workflow/process/PublishAPIClientTest.java
git commit -m "feat: Add HEAD-based verifyPublishPermission to PublishAPIClient"
```

---

## Task 4: `LatteProcess.verifyPublishReadiness` override

**Files:**
- Modify: `src/main/java/org/lattejava/dep/workflow/process/LatteProcess.java`
- Test: `src/test/java/org/lattejava/dep/workflow/process/LatteProcessTest.java`

- [ ] **Step 1: Write the failing tests**

Add these test methods to `LatteProcessTest` (it already imports `CredentialStore`, `Tokens`, `HttpClient`, `HttpServer`, `Files`, `Path`, `StandardCharsets`, `InetSocketAddress`). Add `import org.lattejava.cli.domain.Project;` and `import java.nio.file.Paths;` if missing. Use ports not already used in the file — use 8940/8941/8942.

```java
  @Test
  public void verifyPublishReadinessFailsWhenNotLoggedIn() throws Exception {
    Path configFile = Files.createTempDirectory("latte-process-test").resolve("config.properties");
    LatteProcess process = new LatteProcess(output, "http://localhost:8940", configFile,
        new PublishAPIClient("http://localhost:8940", HttpClient.newHttpClient()));

    Project project = new Project(Paths.get(""), output);
    project.group = "org.example";

    PublishReadiness readiness = process.verifyPublishReadiness(project);

    assertFalse(readiness.ready());
    assertTrue(readiness.message().contains("latte login"), readiness.message());
  }

  @Test
  public void verifyPublishReadinessReadyWhenPermitted() throws Exception {
    Path configFile = Files.createTempDirectory("latte-process-test").resolve("config.properties");
    new CredentialStore(configFile).store(new Tokens("AT", "RT"));

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8941), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      exchange.sendResponseHeaders(200, -1);
      exchange.getResponseBody().close();
    });
    server.start();

    try {
      LatteProcess process = new LatteProcess(output, "http://localhost:8941", configFile,
          new PublishAPIClient("http://localhost:8941", HttpClient.newHttpClient()));

      Project project = new Project(Paths.get(""), output);
      project.group = "org.example";

      PublishReadiness readiness = process.verifyPublishReadiness(project);

      assertTrue(readiness.ready());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void verifyPublishReadinessNotReadyWhenForbiddenAndPersistsRefreshedTokens() throws Exception {
    Path configFile = Files.createTempDirectory("latte-process-test").resolve("config.properties");
    new CredentialStore(configFile).store(new Tokens("AT", "RT"));

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8942), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      exchange.getResponseHeaders().add("X-Access-Token", "new-AT");
      exchange.getResponseHeaders().add("X-Refresh-Token", "new-RT");
      exchange.sendResponseHeaders(403, -1);
      exchange.getResponseBody().close();
    });
    server.start();

    try {
      LatteProcess process = new LatteProcess(output, "http://localhost:8942", configFile,
          new PublishAPIClient("http://localhost:8942", HttpClient.newHttpClient()));

      Project project = new Project(Paths.get(""), output);
      project.group = "org.example";

      PublishReadiness readiness = process.verifyPublishReadiness(project);

      assertFalse(readiness.ready());
      assertTrue(readiness.message().contains("org.example"), readiness.message());

      Tokens persisted = new CredentialStore(configFile).load();
      assertEquals(persisted.accessToken(), "new-AT");
      assertEquals(persisted.refreshToken(), "new-RT");
    } finally {
      server.stop(0);
    }
  }
```

If `assertFalse` is not statically imported in the file, add `import static org.testng.Assert.assertFalse;`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `latte test --test=LatteProcessTest`
Expected: FAIL — compilation error, `verifyPublishReadiness` not defined on `LatteProcess` (it inherits the default but the override / `PublishReadiness` reference will not yet compile against the new signature — confirm failure before implementing).

- [ ] **Step 3: Write the implementation**

In `src/main/java/org/lattejava/dep/workflow/process/LatteProcess.java`:

(a) Add the import (alphabetical, with the other `cli`/`dep` imports):
```java
import org.lattejava.cli.domain.Project;
```

(b) Add the override. Place it after `toString` (alphabetical instance methods: `fetch`, `publish`, `toString`, `verifyPublishReadiness`):

```java
  @Override
  public PublishReadiness verifyPublishReadiness(Project project) {
    CredentialStore credentialStore = new CredentialStore(configFile);
    Tokens tokens = credentialStore.load();
    if (tokens.accessToken() == null) {
      return PublishReadiness.notReady("You are not logged in to the Latte repository. Run [latte login] before releasing.");
    }

    PublishAPIClient.PermissionResponse response = client.verifyPublishPermission(project.group, tokens);
    if (response.refreshedTokens() != null) {
      credentialStore.store(response.refreshedTokens());
    }

    return response.readiness();
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `latte test --test=LatteProcessTest`
Expected: PASS (existing tests plus the three new ones).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/dep/workflow/process/LatteProcess.java src/test/java/org/lattejava/dep/workflow/process/LatteProcessTest.java
git commit -m "feat: LatteProcess verifies login and publish permission via HEAD"
```

---

## Task 5: `release-git` plugin runs the check before tagging

**Files:**
- Modify: `plugins/release-git/src/main/groovy/org/lattejava/plugin/release/ReleaseGitPlugin.groovy`
- Test: `plugins/release-git/src/test/groovy/org/lattejava/plugin/release/ReleaseGitPluginTest.groovy`

- [ ] **Step 1: Publish the updated CLI locally so the plugin compiles against it**

The plugin depends on the CLI artifact, so the interface change from Tasks 1–4 must be integration-published before the plugin can see `verifyPublishReadiness`/`PublishReadiness`.

Run (from the repo root `/Users/bpontarelli/dev/latte-java/cli`): `latte int`
Expected: build + tests pass and the CLI is published to the local integration cache.

- [ ] **Step 2: Write the failing test**

Add this test to `ReleaseGitPluginTest` (which already imports `PublishWorkflow`, `Project`, `RuntimeConfiguration`, `RuntimeFailureException`, and has `assertReleaseDidNotRun()`). Add the import `import org.lattejava.dep.workflow.process.Process` and `import org.lattejava.dep.workflow.process.PublishReadiness` at the top.

This test replaces the project's `publishWorkflow` with one whose single process reports not-ready, then asserts the release fails at the readiness step and no tag is created. The stub uses Groovy's map-to-interface coercion (the default `verifyPublishReadiness` is overridden; `fetch`/`publish` are never called on this path):

```groovy
  @Test
  void releaseFailsWhenPublishNotReady() throws Exception {
    project.dependencies = null
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Replace the publish workflow with a process that reports it cannot publish.
    Process notReadyProcess = [
        verifyPublishReadiness: { Project p -> PublishReadiness.notReady("You are not authorized to publish to the group [${p.group}].".toString()) },
        fetch                 : { item, workflow -> null },
        publish               : { fetchResult -> null }
    ] as Process
    project.publishWorkflow = new PublishWorkflow(notReadyProcess)

    ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.release()
      fail("Should have failed")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("not authorized to publish"), e.message)
    }

    assertReleaseDidNotRun()
  }
```

- [ ] **Step 3: Run test to verify it fails**

Run (Bash tool — set `dangerouslyDisableSandbox: true`): `cd plugins/release-git && latte test --test=ReleaseGitPluginTest`
Expected: FAIL — either compilation error (`verifyPublishReadiness` not yet called by the plugin) or the release proceeds and creates a tag, so `assertReleaseDidNotRun()` fails.

- [ ] **Step 4: Write the implementation**

In `plugins/release-git/src/main/groovy/org/lattejava/plugin/release/ReleaseGitPlugin.groovy`:

(a) Add the import near the other imports:
```groovy
import org.lattejava.dep.workflow.process.PublishReadiness
```

(b) Insert the new step into `release()` between `checkPluginsForIntegrationVersions()` and `tag(git)`:

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
    verifyPublishReadiness()
    tag(git)
    publish()
  }
```

(c) Add the new private method (place it next to the other `check*` methods):

```groovy
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

- [ ] **Step 5: Run test to verify it passes**

Run (Bash tool — set `dangerouslyDisableSandbox: true`): `cd plugins/release-git && latte test --test=ReleaseGitPluginTest`
Expected: PASS — the new test passes AND the existing release tests (`releaseWithDependencies`, `releaseWithoutDependencies`, `releaseWithCustomTag`, `releaseDefaultTag`) still pass, because their `publishWorkflow` uses `CacheProcess`, which inherits the default ready result.

- [ ] **Step 6: Commit**

```bash
git add plugins/release-git/src/main/groovy/org/lattejava/plugin/release/ReleaseGitPlugin.groovy plugins/release-git/src/test/groovy/org/lattejava/plugin/release/ReleaseGitPluginTest.groovy
git commit -m "feat(release-git): Verify publish readiness before creating the Git tag"
```

---

## Task 6: Full verification

- [ ] **Step 1: Run the full CLI test suite**

Run (from repo root): `latte test`
Expected: PASS — no regressions.

- [ ] **Step 2: Run the release-git plugin test suite**

Run (Bash tool — set `dangerouslyDisableSandbox: true`): `cd plugins/release-git && latte test`
Expected: PASS.

- [ ] **Step 3: Final commit (if any incidental changes remain)**

```bash
git status
# Commit only intentional changes; the project.latte working-copy change present at the
# start of this work is unrelated — do NOT stage it unless the user asks.
```
```

---

## Self-Review Notes (resolved)

- **Spec coverage:** PublishReadiness (Task 1), Process default (Task 2), HEAD client method + 401 reword (Task 3), LatteProcess override (Task 4), release-git step before tag (Task 5), tests for each + full verification (Task 6). All spec sections mapped.
- **Type consistency:** `PublishReadiness(ready, message)` — ready case is the constant `PublishReadiness.READY` (not a factory method, to avoid colliding with the auto-generated `ready()` accessor), not-ready case is `PublishReadiness.notReady(msg)`; `PublishAPIClient.verifyPublishPermission(group, tokens) -> PermissionResponse(readiness, refreshedTokens)`; `Process.verifyPublishReadiness(Project) -> PublishReadiness` — names used identically across tasks.
- **No placeholders:** every code and test step shows full code and exact run commands.
