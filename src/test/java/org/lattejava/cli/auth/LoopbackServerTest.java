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
  public void deliversFullResponseEvenWhenServerStopsImmediately() throws Exception {
    // Mirrors LoginCommand: the browser request is in flight while the main thread awaits the code and then immediately
    // stops the server in its finally block. The full HTML response must reach the browser before the server tears down,
    // otherwise the browser renders a blank/broken page. Looped because the failure is a race.
    for (int i = 0; i < 25; i++) {
      int port = 8810 + i;
      LoopbackServer server = new LoopbackServer(port, "good-state");
      server.start();

      HttpResponse<String> response;
      try (var client = HttpClient.newHttpClient()) {
        CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(
            HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/callback?code=the-code&state=good-state")).GET().build(),
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

  @Test
  public void throwsWhenCodeMissing() throws Exception {
    LoopbackServer server = new LoopbackServer(8804, "good-state");
    server.start();
    try {
      get("http://localhost:8804/callback?state=good-state");
      server.awaitCode(Duration.ofSeconds(5));
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      // Expected
    } finally {
      server.stop();
    }
  }

  private void get(String url) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      client.send(
          HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
          HttpResponse.BodyHandlers.ofString()
      );
    }
  }
}
