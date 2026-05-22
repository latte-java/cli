# `latte login` OAuth Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a global `latte login [issuer]` command that authenticates against the Latte FusionAuth IdP via OAuth 2.0 Authorization Code + PKCE (public client) and stores the access/refresh tokens in `~/.config/latte/config.properties`.

**Architecture:** A new `org.lattejava.cli.auth` package holds small, single-purpose, independently-testable units (PKCE generation, loopback callback server, token exchange, JWT decode, credential storage, config/URL building, browser launch). `LoginCommand` orchestrates them and is registered as a global command in `DefaultRunner`. The CLI is a classpath app (no `module-info.java`); everything uses only the JDK (`java.net.http.HttpClient`, `com.sun.net.httpserver.HttpServer`, `java.awt.Desktop`) plus the existing `json-simple` dependency.

**Tech Stack:** Java 25, TestNG, EasyMock (not needed here), json-simple, `latte` build tool.

**Spec:** `docs/superpowers/specs/2026-05-21-latte-login-oauth-design.md`

**Conventions (from `.claude/rules/`):**
- Every file starts with the exact header `/*\n * Copyright (c) 2026 The Latte Project\n * SPDX-License-Identifier: MIT\n */` (no blank line above).
- Runtime values in messages use `[brackets]`, never quotes.
- Acronyms fully uppercase in identifiers (`JWTs`, `OAuthClient`, `URL`, `PKCE`).
- Run a single test class with: `latte test --test=ClassName`.
- Run the whole suite with: `latte test`.

**Fixed CLI client/application ID (used in both code and kickstart):** `cc5f3c9e-b28e-4632-bafe-823363669820`

---

## File Structure

**Create (main):**
- `src/main/java/org/lattejava/cli/auth/AuthConfiguration.java` — issuer resolution + OAuth endpoint/URL building + constants.
- `src/main/java/org/lattejava/cli/auth/PKCE.java` — code verifier/challenge generation.
- `src/main/java/org/lattejava/cli/auth/Tokens.java` — record holding access + refresh tokens.
- `src/main/java/org/lattejava/cli/auth/OAuthClient.java` — authorization-code → token exchange.
- `src/main/java/org/lattejava/cli/auth/LoopbackServer.java` — local HTTP server that captures the redirect.
- `src/main/java/org/lattejava/cli/auth/JWTs.java` — base64url JWT payload claim reader.
- `src/main/java/org/lattejava/cli/auth/CredentialStore.java` — writes tokens into `config.properties`.
- `src/main/java/org/lattejava/cli/auth/Browsers.java` — cross-platform browser launcher.
- `src/main/java/org/lattejava/cli/command/LoginCommand.java` — orchestrates the flow.

**Modify (main):**
- `src/main/java/org/lattejava/cli/runtime/DefaultRunner.java` — register `"login"` in `COMMANDS`.
- `src/main/java/org/lattejava/cli/runtime/Main.java` — add a `login` help line.

**Create (test):**
- `src/test/java/org/lattejava/cli/auth/AuthConfigurationTest.java`
- `src/test/java/org/lattejava/cli/auth/PKCETest.java`
- `src/test/java/org/lattejava/cli/auth/JWTsTest.java`
- `src/test/java/org/lattejava/cli/auth/CredentialStoreTest.java`
- `src/test/java/org/lattejava/cli/auth/OAuthClientTest.java`
- `src/test/java/org/lattejava/cli/auth/LoopbackServerTest.java`

**Modify (test):**
- `src/test/java/org/lattejava/cli/runtime/DefaultRunnerTest.java` — assert `login` is registered.

**Modify (kickstart):**
- `src/test/fusionauth/kickstart/kickstart.json` — add CLI application + user registrations.

---

## Task 1: AuthConfiguration

Resolves the issuer (argument or default), exposes constants, and builds the authorize URL and token endpoint.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/AuthConfiguration.java`
- Test: `src/test/java/org/lattejava/cli/auth/AuthConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the AuthConfiguration.
 *
 * @author Brian Pontarelli
 */
public class AuthConfigurationTest extends BaseUnitTest {
  @Test
  public void authorizeURLContainsRequiredParameters() {
    AuthConfiguration config = new AuthConfiguration("http://localhost:9011");
    String url = config.authorizeURL("the-state", "the-challenge");

    assertTrue(url.startsWith("http://localhost:9011/oauth2/authorize?"), url);
    assertTrue(url.contains("response_type=code"), url);
    assertTrue(url.contains("client_id=" + AuthConfiguration.CLIENT_ID), url);
    assertTrue(url.contains("code_challenge=the-challenge"), url);
    assertTrue(url.contains("code_challenge_method=S256"), url);
    assertTrue(url.contains("state=the-state"), url);

    String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
    assertTrue(decoded.contains("redirect_uri=http://localhost:8888/callback"), decoded);
    assertTrue(decoded.contains("scope=openid offline_access"), decoded);
  }

  @Test
  public void defaultsIssuerWhenNullOrBlank() {
    assertEquals(new AuthConfiguration(null).issuer(), "https://auth.lattejava.org");
    assertEquals(new AuthConfiguration("   ").issuer(), "https://auth.lattejava.org");
  }

  @Test
  public void stripsTrailingSlash() {
    assertEquals(new AuthConfiguration("http://localhost:9011/").issuer(), "http://localhost:9011");
    assertEquals(new AuthConfiguration("http://localhost:9011//").issuer(), "http://localhost:9011");
  }

  @Test
  public void tokenEndpoint() {
    assertEquals(new AuthConfiguration("http://localhost:9011").tokenEndpoint().toString(),
        "http://localhost:9011/oauth2/token");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=AuthConfigurationTest`
Expected: FAIL — `AuthConfiguration` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Holds the resolved OAuth issuer and the hardcoded public-client settings, and builds the OAuth endpoint URLs used by
 * the {@code latte login} command.
 *
 * @author Brian Pontarelli
 */
public class AuthConfiguration {
  public static final int CALLBACK_PORT = 8888;
  public static final String CLIENT_ID = "cc5f3c9e-b28e-4632-bafe-823363669820";
  public static final String DEFAULT_ISSUER = "https://auth.lattejava.org";
  public static final String REDIRECT_URI = "http://localhost:8888/callback";
  public static final String SCOPES = "openid offline_access";

  private final String issuer;

  public AuthConfiguration(String issuer) {
    String resolved = (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer.trim();
    while (resolved.endsWith("/")) {
      resolved = resolved.substring(0, resolved.length() - 1);
    }
    this.issuer = resolved;
  }

  public String authorizeURL(String state, String codeChallenge) {
    return issuer + "/oauth2/authorize?response_type=code" +
        "&client_id=" + encode(CLIENT_ID) +
        "&redirect_uri=" + encode(REDIRECT_URI) +
        "&scope=" + encode(SCOPES) +
        "&code_challenge=" + encode(codeChallenge) +
        "&code_challenge_method=S256" +
        "&state=" + encode(state);
  }

  public String issuer() {
    return issuer;
  }

  public URI tokenEndpoint() {
    return URI.create(issuer + "/oauth2/token");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
```

Note: `URLEncoder.encode` encodes a space as `+`; the test decodes with `URLDecoder` (which turns `+` back into a space) before asserting on `redirect_uri`/`scope`, so this passes. The `client_id`/`code_challenge`/`state` assertions use values with no special characters, so they match the raw URL directly.

- [ ] **Step 4: Run the test to verify it passes**

Run: `latte test --test=AuthConfigurationTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/AuthConfiguration.java src/test/java/org/lattejava/cli/auth/AuthConfigurationTest.java
git commit -m "feat: add AuthConfiguration for latte login OAuth endpoints"
```

---

## Task 2: PKCE

Generates the PKCE `code_verifier` and the S256 `code_challenge`.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/PKCE.java`
- Test: `src/test/java/org/lattejava/cli/auth/PKCETest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests PKCE generation.
 *
 * @author Brian Pontarelli
 */
public class PKCETest extends BaseUnitTest {
  @Test
  public void challengeIsBase64URLSHA256OfVerifier() throws Exception {
    PKCE pkce = PKCE.generate();

    byte[] hash = MessageDigest.getInstance("SHA-256").digest(pkce.verifier().getBytes(StandardCharsets.US_ASCII));
    String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    assertEquals(pkce.challenge(), expected);
  }

  @Test
  public void verifierIsURLSafeAndUnique() {
    PKCE first = PKCE.generate();
    PKCE second = PKCE.generate();

    assertNotEquals(first.verifier(), second.verifier());
    assertTrue(first.verifier().length() >= 43, first.verifier());
    assertFalse(first.verifier().contains("+"), first.verifier());
    assertFalse(first.verifier().contains("/"), first.verifier());
    assertFalse(first.verifier().contains("="), first.verifier());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=PKCETest`
Expected: FAIL — `PKCE` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.lattejava.cli.runtime.RuntimeFailureException;

/**
 * A PKCE (RFC 7636) code verifier and its derived S256 code challenge.
 *
 * @author Brian Pontarelli
 */
public record PKCE(String verifier, String challenge) {
  public static PKCE generate() {
    byte[] randomBytes = new byte[32];
    new SecureRandom().nextBytes(randomBytes);
    String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
      return new PKCE(verifier, challenge);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeFailureException("SHA-256 is not available in this JVM. Message was [" + e.getMessage() + "]");
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `latte test --test=PKCETest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/PKCE.java src/test/java/org/lattejava/cli/auth/PKCETest.java
git commit -m "feat: add PKCE verifier/challenge generation"
```

---

## Task 3: JWTs

Decodes a JWT payload (no signature check) and reads a string claim.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/JWTs.java`
- Test: `src/test/java/org/lattejava/cli/auth/JWTsTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

/**
 * Tests JWT payload decoding.
 *
 * @author Brian Pontarelli
 */
public class JWTsTest extends BaseUnitTest {
  @Test
  public void malformedTokenThrows() {
    try {
      JWTs.claim("not-a-jwt", "email");
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      // Expected
    }
  }

  @Test
  public void missingClaimReturnsNull() {
    assertNull(JWTs.claim(token("{\"sub\":\"123\"}"), "email"));
  }

  @Test
  public void readsClaim() {
    assertEquals(JWTs.claim(token("{\"email\":\"brian@pontarelli.com\"}"), "email"), "brian@pontarelli.com");
  }

  private static String token(String payloadJSON) {
    String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJSON.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".signature";
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=JWTsTest`
Expected: FAIL — `JWTs` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.lattejava.cli.runtime.RuntimeFailureException;

/**
 * Reads claims from a JWT by base64url-decoding its payload segment. Does not verify the signature; the token is
 * received directly from the IdP over TLS during login, so verification would be redundant.
 *
 * @author Brian Pontarelli
 */
public final class JWTs {
  private JWTs() {
  }

  public static String claim(String jwt, String name) {
    String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      throw new RuntimeFailureException("Malformed JWT [" + jwt + "]");
    }

    try {
      byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
      JSONObject json = (JSONObject) new JSONParser().parse(new String(payload, StandardCharsets.UTF_8));
      Object value = json.get(name);
      return value == null ? null : value.toString();
    } catch (IllegalArgumentException | ParseException e) {
      throw new RuntimeFailureException("Could not decode the JWT payload. Message was [" + e.getMessage() + "]");
    }
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `latte test --test=JWTsTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/JWTs.java src/test/java/org/lattejava/cli/auth/JWTsTest.java
git commit -m "feat: add JWT payload claim decoding"
```

---

## Task 4: Tokens + CredentialStore

`Tokens` is a small record. `CredentialStore` merges the tokens into `config.properties` and restricts permissions.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/Tokens.java`
- Create: `src/main/java/org/lattejava/cli/auth/CredentialStore.java`
- Test: `src/test/java/org/lattejava/cli/auth/CredentialStoreTest.java`

- [ ] **Step 1: Create the Tokens record (no test of its own — exercised by later tasks)**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

/**
 * The OAuth tokens returned from a successful authorization-code exchange.
 *
 * @author Brian Pontarelli
 */
public record Tokens(String accessToken, String refreshToken) {
}
```

- [ ] **Step 2: Write the failing test**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the CredentialStore.
 *
 * @author Brian Pontarelli
 */
public class CredentialStoreTest extends BaseUnitTest {
  @Test
  public void preservesExistingPropertiesAndStoresTokens() throws Exception {
    Path dir = Files.createTempDirectory("latte-credential-test");
    Path configFile = dir.resolve("config.properties");
    Files.writeString(configFile, "existing.key=existing-value\n");

    new CredentialStore(configFile).store(new Tokens("the-access-token", "the-refresh-token"));

    Properties loaded = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      loaded.load(is);
    }

    assertEquals(loaded.getProperty("existing.key"), "existing-value");
    assertEquals(loaded.getProperty("latte.auth.accessToken"), "the-access-token");
    assertEquals(loaded.getProperty("latte.auth.refreshToken"), "the-refresh-token");
  }

  @Test
  public void createsFileAndParentDirectoriesWhenMissing() throws Exception {
    Path dir = Files.createTempDirectory("latte-credential-test");
    Path configFile = dir.resolve("nested/config.properties");

    new CredentialStore(configFile).store(new Tokens("AT", "RT"));

    Properties loaded = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      loaded.load(is);
    }
    assertEquals(loaded.getProperty("latte.auth.accessToken"), "AT");
  }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `latte test --test=CredentialStoreTest`
Expected: FAIL — `CredentialStore` does not exist (compilation error).

- [ ] **Step 4: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

import org.lattejava.cli.runtime.RuntimeFailureException;

/**
 * Writes the OAuth tokens into the Latte global configuration file, merging with any properties already present and
 * restricting the file permissions to the owner where the filesystem supports it.
 *
 * @author Brian Pontarelli
 */
public class CredentialStore {
  static final String ACCESS_TOKEN_KEY = "latte.auth.accessToken";
  static final String REFRESH_TOKEN_KEY = "latte.auth.refreshToken";

  private final Path configFile;

  public CredentialStore(Path configFile) {
    this.configFile = configFile;
  }

  public void store(Tokens tokens) {
    Properties properties = new Properties();
    if (Files.isRegularFile(configFile)) {
      try (InputStream is = Files.newInputStream(configFile)) {
        properties.load(is);
      } catch (IOException e) {
        throw new RuntimeFailureException("Unable to read the configuration file [" + configFile + "]. Message was [" + e.getMessage() + "]");
      }
    }

    properties.setProperty(ACCESS_TOKEN_KEY, tokens.accessToken());
    if (tokens.refreshToken() != null) {
      properties.setProperty(REFRESH_TOKEN_KEY, tokens.refreshToken());
    }

    try {
      Path parent = configFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try (OutputStream os = Files.newOutputStream(configFile)) {
        properties.store(os, "Latte configuration");
      }
      restrictPermissions();
    } catch (IOException e) {
      throw new RuntimeFailureException("Unable to write the configuration file [" + configFile + "]. Message was [" + e.getMessage() + "]");
    }
  }

  private void restrictPermissions() throws IOException {
    if (configFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(configFile, PosixFilePermissions.fromString("rw-------"));
    }
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `latte test --test=CredentialStoreTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/Tokens.java src/main/java/org/lattejava/cli/auth/CredentialStore.java src/test/java/org/lattejava/cli/auth/CredentialStoreTest.java
git commit -m "feat: add Tokens record and CredentialStore"
```

---

## Task 5: OAuthClient

POSTs the authorization code (plus PKCE verifier, no secret) to the token endpoint and parses the response.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/OAuthClient.java`
- Test: `src/test/java/org/lattejava/cli/auth/OAuthClientTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the OAuthClient against a stub token endpoint.
 *
 * @author Brian Pontarelli
 */
public class OAuthClientTest extends BaseUnitTest {
  @Test
  public void exchangeCodeParsesTokens() throws Exception {
    HttpServer tokenServer = HttpServer.create(new InetSocketAddress("localhost", 8765), 0);
    tokenServer.createContext("/oauth2/token", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(body.contains("grant_type=authorization_code"), body);
      assertTrue(body.contains("code=the-code"), body);
      assertTrue(body.contains("code_verifier=the-verifier"), body);
      assertTrue(body.contains("client_id=" + AuthConfiguration.CLIENT_ID), body);

      byte[] response = "{\"access_token\":\"AT\",\"refresh_token\":\"RT\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.getResponseBody().close();
    });
    tokenServer.start();

    try {
      AuthConfiguration config = new AuthConfiguration("http://localhost:8765");
      Tokens tokens = new OAuthClient(config, HttpClient.newHttpClient()).exchangeCode("the-code", "the-verifier");
      assertEquals(tokens.accessToken(), "AT");
      assertEquals(tokens.refreshToken(), "RT");
    } finally {
      tokenServer.stop(0);
    }
  }

  @Test
  public void exchangeCodeThrowsOnNon200() throws Exception {
    HttpServer tokenServer = HttpServer.create(new InetSocketAddress("localhost", 8766), 0);
    tokenServer.createContext("/oauth2/token", exchange -> {
      byte[] response = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(400, response.length);
      exchange.getResponseBody().write(response);
      exchange.getResponseBody().close();
    });
    tokenServer.start();

    try {
      AuthConfiguration config = new AuthConfiguration("http://localhost:8766");
      new OAuthClient(config, HttpClient.newHttpClient()).exchangeCode("bad", "verifier");
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("400"), e.getMessage());
    } finally {
      tokenServer.stop(0);
    }
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=OAuthClientTest`
Expected: FAIL — `OAuthClient` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.lattejava.cli.runtime.RuntimeFailureException;

/**
 * Exchanges an OAuth authorization code for tokens at the IdP token endpoint, authenticating as a public client using
 * the PKCE code verifier (no client secret).
 *
 * @author Brian Pontarelli
 */
public class OAuthClient {
  private final AuthConfiguration configuration;
  private final HttpClient httpClient;

  public OAuthClient(AuthConfiguration configuration, HttpClient httpClient) {
    this.configuration = configuration;
    this.httpClient = httpClient;
  }

  public Tokens exchangeCode(String code, String codeVerifier) {
    String form = "grant_type=authorization_code" +
        "&code=" + encode(code) +
        "&redirect_uri=" + encode(AuthConfiguration.REDIRECT_URI) +
        "&client_id=" + encode(AuthConfiguration.CLIENT_ID) +
        "&code_verifier=" + encode(codeVerifier);

    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(configuration.tokenEndpoint())
                                     .header("Content-Type", "application/x-www-form-urlencoded")
                                     .header("Accept", "application/json")
                                     .timeout(Duration.ofSeconds(30))
                                     .POST(HttpRequest.BodyPublishers.ofString(form))
                                     .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeFailureException("The token request failed with status [" + response.statusCode() + "] and body [" + response.body() + "]");
      }

      JSONObject json = (JSONObject) new JSONParser().parse(response.body());
      String accessToken = (String) json.get("access_token");
      String refreshToken = (String) json.get("refresh_token");
      if (accessToken == null) {
        throw new RuntimeFailureException("The token response did not contain an access token. Body was [" + response.body() + "]");
      }
      return new Tokens(accessToken, refreshToken);
    } catch (IOException | ParseException e) {
      throw new RuntimeFailureException("The token request failed. Message was [" + e.getMessage() + "]");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeFailureException("The token request was interrupted.");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `latte test --test=OAuthClientTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/OAuthClient.java src/test/java/org/lattejava/cli/auth/OAuthClientTest.java
git commit -m "feat: add OAuthClient token exchange"
```

---

## Task 6: LoopbackServer

Starts a local HTTP server, captures the `/callback` redirect, validates `state`, and renders a completion page. The expected `state` is supplied at construction so the handler can validate even if the callback arrives before `awaitCode` is called.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/LoopbackServer.java`
- Test: `src/test/java/org/lattejava/cli/auth/LoopbackServerTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * Tests the LoopbackServer.
 *
 * @author Brian Pontarelli
 */
public class LoopbackServerTest extends BaseUnitTest {
  @Test
  public void capturesCodeWhenStateMatches() throws Exception {
    LoopbackServer server = new LoopbackServer(8801, "good-state");
    server.start();
    try {
      get("http://localhost:8801/callback?code=the-code&state=good-state");
      assertEquals(server.awaitCode(Duration.ofSeconds(5)), "the-code");
    } finally {
      server.stop();
    }
  }

  @Test
  public void throwsOnErrorParameter() throws Exception {
    LoopbackServer server = new LoopbackServer(8802, "good-state");
    server.start();
    try {
      get("http://localhost:8802/callback?error=access_denied&state=good-state");
      server.awaitCode(Duration.ofSeconds(5));
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      // Expected
    } finally {
      server.stop();
    }
  }

  @Test
  public void throwsOnStateMismatch() throws Exception {
    LoopbackServer server = new LoopbackServer(8803, "good-state");
    server.start();
    try {
      get("http://localhost:8803/callback?code=the-code&state=wrong-state");
      server.awaitCode(Duration.ofSeconds(5));
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      // Expected
    } finally {
      server.stop();
    }
  }

  private void get(String url) throws Exception {
    HttpClient.newHttpClient().send(
        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=LoopbackServerTest`
Expected: FAIL — `LoopbackServer` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.lattejava.cli.runtime.RuntimeFailureException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A single-use local HTTP server that listens on the loopback interface for the OAuth redirect, validates the {@code
 * state} parameter, and exposes the captured authorization code.
 *
 * @author Brian Pontarelli
 */
public class LoopbackServer {
  private final CompletableFuture<String> codeFuture = new CompletableFuture<>();
  private final String expectedState;
  private final int port;

  private HttpServer server;

  public LoopbackServer(int port, String expectedState) {
    this.port = port;
    this.expectedState = expectedState;
  }

  public String awaitCode(Duration timeout) {
    try {
      return codeFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeFailureException("Timed out after [" + timeout.toSeconds() + "] seconds waiting for the login to complete in the browser.");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeFailureException failure) {
        throw failure;
      }
      throw new RuntimeFailureException("The login failed. Message was [" + e.getCause().getMessage() + "]");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeFailureException("The login was interrupted.");
    }
  }

  public void start() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    } catch (IOException e) {
      throw new RuntimeFailureException("Could not start the local login server on port [" + port + "]. It may already be in use. Message was [" + e.getMessage() + "]");
    }
    server.createContext("/callback", this::handle);
    server.start();
  }

  public void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());

    String message;
    if (params.containsKey("error")) {
      codeFuture.completeExceptionally(new RuntimeFailureException("Authorization failed with error [" + params.get("error") + "]"));
      message = "Login failed. You can close this tab and return to the terminal.";
    } else if (!Objects.equals(expectedState, params.get("state"))) {
      codeFuture.completeExceptionally(new RuntimeFailureException("The login response state did not match. This may indicate a CSRF attempt or a stale login."));
      message = "Login failed. You can close this tab and return to the terminal.";
    } else {
      codeFuture.complete(params.get("code"));
      message = "Login complete. You can close this tab and return to the terminal.";
    }

    byte[] body = ("<!DOCTYPE html><html><body><h2>" + message + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.getResponseBody().close();
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> result = new HashMap<>();
    if (query == null || query.isEmpty()) {
      return result;
    }
    for (String pair : query.split("&")) {
      int equals = pair.indexOf('=');
      if (equals > 0) {
        result.put(pair.substring(0, equals), pair.substring(equals + 1));
      }
    }
    return result;
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `latte test --test=LoopbackServerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/LoopbackServer.java src/test/java/org/lattejava/cli/auth/LoopbackServerTest.java
git commit -m "feat: add LoopbackServer for OAuth redirect capture"
```

---

## Task 7: Browsers

Cross-platform browser launcher. No automated test (side-effecting and environment-dependent); verified manually in Task 9.

**Files:**
- Create: `src/main/java/org/lattejava/cli/auth/Browsers.java`

- [ ] **Step 1: Write the implementation**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import org.lattejava.output.Output;

/**
 * Opens the user's default web browser to a URL, falling back to printing the URL when no browser can be launched (for
 * example over SSH).
 *
 * @author Brian Pontarelli
 */
public final class Browsers {
  private Browsers() {
  }

  public static void open(String url, Output output) {
    output.infoln("Opening your browser to log in. If it does not open automatically, visit:");
    output.infoln("%s", url);

    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI.create(url));
        return;
      }
    } catch (Exception e) {
      // Fall through to the OS-specific command.
    }

    String os = System.getProperty("os.name").toLowerCase();
    try {
      if (os.contains("mac")) {
        new ProcessBuilder("open", url).start();
      } else if (os.contains("nix") || os.contains("nux")) {
        new ProcessBuilder("xdg-open", url).start();
      }
    } catch (IOException e) {
      // The printed URL above is the fallback.
    }
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `latte build`
Expected: BUILD succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/lattejava/cli/auth/Browsers.java
git commit -m "feat: add cross-platform browser launcher"
```

---

## Task 8: LoginCommand + registration + help

Wire the components into the command, register it as a global command, and add the help line.

**Files:**
- Create: `src/main/java/org/lattejava/cli/command/LoginCommand.java`
- Modify: `src/main/java/org/lattejava/cli/runtime/DefaultRunner.java:39-43`
- Modify: `src/main/java/org/lattejava/cli/runtime/Main.java:108-110`
- Modify: `src/test/java/org/lattejava/cli/runtime/DefaultRunnerTest.java`

- [ ] **Step 1: Write the failing test (registration)**

Add this test method to `DefaultRunnerTest` (keep existing imports; add `import org.lattejava.cli.command.LoginCommand;` and, if not present, `import static org.testng.Assert.assertTrue;`):

```java
  @Test
  public void loginCommandIsRegistered() {
    assertTrue(DefaultRunner.COMMANDS.get("login") instanceof LoginCommand);
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `latte test --test=DefaultRunnerTest`
Expected: FAIL — `LoginCommand` does not exist / `COMMANDS` has no `login` entry.

- [ ] **Step 3: Create LoginCommand**

```java
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.command;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.lattejava.cli.auth.AuthConfiguration;
import org.lattejava.cli.auth.Browsers;
import org.lattejava.cli.auth.CredentialStore;
import org.lattejava.cli.auth.JWTs;
import org.lattejava.cli.auth.LoopbackServer;
import org.lattejava.cli.auth.OAuthClient;
import org.lattejava.cli.auth.PKCE;
import org.lattejava.cli.auth.Tokens;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.output.Output;
import org.lattejava.util.LattePaths;

/**
 * Logs the user into the Latte IdP using the OAuth 2.0 Authorization Code flow with PKCE, then stores the resulting
 * access and refresh tokens in the global configuration file.
 * <p>
 * Usage:
 * <pre>
 *   latte login                          # production IdP
 *   latte login http://localhost:9011    # local FusionAuth Docker setup
 * </pre>
 *
 * @author Brian Pontarelli
 */
public class LoginCommand implements Command {
  private final HttpClient httpClient;

  public LoginCommand() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  LoginCommand(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    String issuer = configuration.args.isEmpty() ? null : configuration.args.getFirst();
    AuthConfiguration authConfiguration = new AuthConfiguration(issuer);
    PKCE pkce = PKCE.generate();
    String state = randomState();

    LoopbackServer server = new LoopbackServer(AuthConfiguration.CALLBACK_PORT, state);
    server.start();

    String code;
    try {
      Browsers.open(authConfiguration.authorizeURL(state, pkce.challenge()), output);
      code = server.awaitCode(Duration.ofMinutes(2));
    } finally {
      server.stop();
    }

    Tokens tokens = new OAuthClient(authConfiguration, httpClient).exchangeCode(code, pkce.verifier());
    new CredentialStore(LattePaths.get().configDir().resolve("config.properties")).store(tokens);

    String email = JWTs.claim(tokens.accessToken(), "email");
    if (email != null) {
      output.infoln("Logged in as [%s]", email);
    } else {
      output.infoln("Login successful.");
    }
  }

  private static String randomState() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
```

- [ ] **Step 4: Register the command in DefaultRunner**

In `src/main/java/org/lattejava/cli/runtime/DefaultRunner.java`, add the import (alphabetized with the other command imports):

```java
import org.lattejava.cli.command.LoginCommand;
```

Replace the `COMMANDS` map (currently lines 39-43) with:

```java
  public static final Map<String, Command> COMMANDS = Map.of(
      "init", new InitCommand(),
      "install", new InstallCommand(),
      "login", new LoginCommand(),
      "upgrade", new UpgradeCommand()
  );
```

- [ ] **Step 5: Add the help line in Main**

In `src/main/java/org/lattejava/cli/runtime/Main.java`, inside `printHelp`, add this after the `install` block (after line 107) and before the `upgrade` block, keeping the commands in alphabetical order:

```java
    output.infoln("   login           Logs into the Latte IdP and stores the auth tokens");
    output.infoln("                   Usage: latte login [issuer-url]");
    output.infoln("                   Defaults to the production IdP; pass a URL for local testing");
```

- [ ] **Step 6: Run the registration test to verify it passes**

Run: `latte test --test=DefaultRunnerTest`
Expected: PASS (including `loginCommandIsRegistered`).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/lattejava/cli/command/LoginCommand.java src/main/java/org/lattejava/cli/runtime/DefaultRunner.java src/main/java/org/lattejava/cli/runtime/Main.java src/test/java/org/lattejava/cli/runtime/DefaultRunnerTest.java
git commit -m "feat: add latte login command and register it"
```

---

## Task 9: FusionAuth kickstart — CLI application + registrations

Add the public CLI application and register the existing users against it so `latte login http://localhost:9011` works out of the box. This is config-only; verification is the manual run at the end.

**Files:**
- Modify: `src/test/fusionauth/kickstart/kickstart.json`

- [ ] **Step 1: Add the `cliApplicationId` variable**

In the `"variables"` object, add this entry (after `"defaultTenantId"`):

```json
    "cliApplicationId": "cc5f3c9e-b28e-4632-bafe-823363669820",
```

- [ ] **Step 2: Add the CLI application request**

In the `"requests"` array, immediately after the existing `POST /api/application/#{applicationId}` request (the "Latte App" block that ends around line 301) and before the `POST /api/identity-provider/#{githubIdpId}` request, insert:

```json
    {
      "method": "POST",
      "url": "/api/application/#{cliApplicationId}",
      "tenantId": "#{defaultTenantId}",
      "body": {
        "application": {
          "name": "Latte CLI",
          "oauthConfiguration": {
            "authorizedRedirectURLs": [
              "http://localhost:8888/callback"
            ],
            "clientAuthenticationPolicy": "NotRequired",
            "proofKeyForCodeExchangePolicy": "Required",
            "enabledGrants": [
              "authorization_code",
              "refresh_token"
            ],
            "generateRefreshTokens": true,
            "requireRegistration": true
          },
          "jwtConfiguration": {
            "enabled": true,
            "accessTokenKeyId": "#{asymmetricKeyId}",
            "idTokenKeyId": "#{asymmetricKeyId}"
          },
          "lambdaConfiguration": {
            "accessTokenPopulateId": "#{accessTokenPopulateLambdaId}"
          },
          "registrationConfiguration": {
            "enabled": true,
            "type": "advanced",
            "formId": "#{registrationFormId}"
          },
          "formConfiguration": {
            "selfServiceFormId": "#{selfServiceFormId}"
          }
        }
      }
    },
```

- [ ] **Step 3: Register the existing users against the CLI application**

At the end of the `"requests"` array, after the `POST /api/user/registration/#{ordinaryUserId}` request (the last request, ending around line 365), add these two requests. The users already exist (created by the earlier registration requests), so these only add a registration for the CLI application — no embedded `user` object:

```json
    {
      "method": "POST",
      "url": "/api/user/registration/#{adminUserId}",
      "body": {
        "registration": {
          "applicationId": "#{cliApplicationId}",
          "roles": [
            "admin"
          ]
        }
      }
    },
    {
      "method": "POST",
      "url": "/api/user/registration/#{ordinaryUserId}",
      "body": {
        "registration": {
          "applicationId": "#{cliApplicationId}"
        }
      }
    }
```

Make sure the request that was previously last now has a trailing comma after its closing `}` and that the final request in the array does not.

- [ ] **Step 4: Validate the JSON**

Run: `cat src/test/fusionauth/kickstart/kickstart.json | python3 -m json.tool > /dev/null && echo VALID`
Expected: `VALID` (no JSON syntax errors).

- [ ] **Step 5: Commit**

```bash
git add src/test/fusionauth/kickstart/kickstart.json
git commit -m "feat: add Latte CLI public application to FusionAuth kickstart"
```

---

## Task 10: Full suite + manual verification

- [ ] **Step 1: Run the full test suite**

Run: `latte test`
Expected: PASS — all tests including the six new `auth` test classes and the updated `DefaultRunnerTest`.

- [ ] **Step 2: Start FusionAuth locally**

Run (from `src/test/fusionauth`): `docker compose up -d`
Wait until `http://localhost:9011` is healthy (FusionAuth status endpoint returns 200). The kickstart provisions the "Latte CLI" application and registers `admin@lattejava.org` / `test@lattejava.org` (both password `password`).

- [ ] **Step 3: Build the CLI bundle and run login**

Run: `latte int` (or use the locally built bundle), then run: `latte login http://localhost:9011`
Expected:
- The browser opens to the FusionAuth login page.
- After logging in as `test@lattejava.org` / `password`, the browser shows "Login complete. You can close this tab…".
- The terminal prints `Logged in as [test@lattejava.org]`.

- [ ] **Step 4: Confirm tokens were stored**

Run: `cat ~/.config/latte/config.properties`
Expected: the file contains `latte.auth.accessToken` and `latte.auth.refreshToken` entries. On macOS/Linux confirm permissions are `-rw-------` with `ls -l ~/.config/latte/config.properties`.

- [ ] **Step 5: Tear down**

Run (from `src/test/fusionauth`): `docker compose down`

---

## Self-Review Notes

- **Spec coverage:** Flow (Tasks 1-8), public-client/PKCE (Tasks 1, 2, 5, 9), fixed loopback port 8888 (Tasks 1, 6, 8), issuer-as-argument (Tasks 1, 8), token storage keys (Task 4), decode-payload-only identity (Task 3, 8), kickstart CLI app reusing lambda/forms + user registrations (Task 9), error handling (Tasks 5, 6), unit + integration tests (Tasks 1-6), manual verification (Task 10). All spec sections map to a task.
- **No placeholders:** every code step contains complete, compiling code.
- **Type consistency:** `Tokens(accessToken, refreshToken)`, `PKCE(verifier, challenge)`, `AuthConfiguration.CLIENT_ID`/`CALLBACK_PORT`/`REDIRECT_URI`, `LoopbackServer(port, expectedState)` + `awaitCode(Duration)`, `OAuthClient(AuthConfiguration, HttpClient).exchangeCode(code, verifier)`, `CredentialStore(Path).store(Tokens)`, `JWTs.claim(jwt, name)`, `Browsers.open(url, output)` are used identically across tasks.
