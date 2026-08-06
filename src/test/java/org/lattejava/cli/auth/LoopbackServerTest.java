/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.concurrent.*;

import org.lattejava.*;
import org.lattejava.cli.runtime.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Tests the LoopbackServer.
 *
 * @author Brian Pontarelli
 */
public class LoopbackServerTest extends BaseUnitTest {
  @Test
  public void bindsDistinctEphemeralPorts() {
    // Two logins running at once must not collide, which is the whole reason the port is ephemeral rather than fixed.
    LoopbackServer first = new LoopbackServer("good-state");
    LoopbackServer second = new LoopbackServer("good-state");
    first.start();
    second.start();

    try {
      assertTrue(first.port() > 0, "Port was [" + first.port() + "]");
      assertTrue(second.port() > 0, "Port was [" + second.port() + "]");
      assertNotEquals(first.port(), second.port());
      // The IPv4 loopback literal, not "localhost" — see RFC 8252 section 8.3.
      assertEquals(first.redirectURI(), "http://127.0.0.1:" + first.port() + "/callback");
    } finally {
      first.stop();
      second.stop();
    }
  }

  @Test
  public void capturesCodeWhenStateMatches() throws Exception {
    LoopbackServer server = new LoopbackServer("good-state");
    server.start();
    try {
      get(server.redirectURI() + "?code=the-code&state=good-state");
      assertEquals(server.awaitCode(Duration.ofSeconds(5)), "the-code");
    } finally {
      server.stop();
    }
  }

  @Test
  public void decodesPercentEncodedCode() throws Exception {
    // The hand-rolled query parser this replaced returned raw, still-encoded values. The Latte server decodes them, so
    // a code containing reserved characters arrives intact rather than being double-encoded into the token request.
    LoopbackServer server = new LoopbackServer("good-state");
    server.start();
    try {
      get(server.redirectURI() + "?code=a%2Fb%2Bc%3Dd&state=good-state");
      assertEquals(server.awaitCode(Duration.ofSeconds(5)), "a/b+c=d");
    } finally {
      server.stop();
    }
  }

  @Test
  public void deliversFullResponseEvenWhenServerStopsImmediately() throws Exception {
    // Mirrors LoginCommand: the browser request is in flight while the main thread awaits the code and then immediately
    // stops the server in its finally block. The full HTML response must reach the browser before the server tears down,
    // otherwise the browser renders a blank/broken page. Looped because the failure is a race.
    for (int i = 0; i < 25; i++) {
      LoopbackServer server = new LoopbackServer("good-state");
      server.start();

      HttpResponse<String> response;
      try (var client = HttpClient.newHttpClient()) {
        CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(
            HttpRequest.newBuilder().uri(URI.create(server.redirectURI() + "?code=the-code&state=good-state")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(server.awaitCode(Duration.ofSeconds(5)), "the-code");
        server.stop();
        response = responseFuture.get(5, TimeUnit.SECONDS);
      }

      assertEquals(response.statusCode(), 200, "Iteration [" + i + "]");
      assertTrue(response.body().contains("ON THE HOUSE"), "Iteration [" + i + "] body was [" + response.body() + "]");
    }
  }

  @Test
  public void ignoresOtherPaths() throws Exception {
    // The JDK server only dispatched its one registered context; the Latte server hands every path to the single
    // handler, so the handler itself has to turn away anything that is not the callback instead of resolving the login.
    LoopbackServer server = new LoopbackServer("good-state");
    server.start();
    try {
      assertEquals(get("http://127.0.0.1:" + server.port() + "/favicon.ico?code=nope&state=good-state").statusCode(), 404);

      // The future is still open, so the real callback still completes the login.
      get(server.redirectURI() + "?code=the-code&state=good-state");
      assertEquals(server.awaitCode(Duration.ofSeconds(5)), "the-code");
    } finally {
      server.stop();
    }
  }

  @Test
  public void portThrowsBeforeStart() {
    LoopbackServer server = new LoopbackServer("good-state");
    try {
      server.port();
      fail("Should have thrown");
    } catch (IllegalStateException e) {
      // Expected
    }
  }

  @Test
  public void throwsOnErrorParameter() throws Exception {
    LoopbackServer server = new LoopbackServer("good-state");
    server.start();
    try {
      get(server.redirectURI() + "?error=access_denied&state=good-state");
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
    LoopbackServer server = new LoopbackServer("good-state");
    server.start();
    try {
      get(server.redirectURI() + "?code=the-code&state=wrong-state");
      server.awaitCode(Duration.ofSeconds(5));
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      // Expected
    } finally {
      server.stop();
    }
  }

  @Test
  public void throwsWhenCodeMissing() throws Exception {
    LoopbackServer server = new LoopbackServer("good-state");
    server.start();
    try {
      get(server.redirectURI() + "?state=good-state");
      server.awaitCode(Duration.ofSeconds(5));
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      // Expected
    } finally {
      server.stop();
    }
  }

  private HttpResponse<String> get(String url) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
          HttpResponse.BodyHandlers.ofString()
      );
    }
  }
}
