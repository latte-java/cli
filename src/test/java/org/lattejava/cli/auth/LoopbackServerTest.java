/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;

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
