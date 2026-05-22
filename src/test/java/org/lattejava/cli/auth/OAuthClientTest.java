/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.*;
import java.nio.charset.*;

import com.sun.net.httpserver.*;
import org.lattejava.*;
import org.lattejava.cli.runtime.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

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
      assertTrue(body.contains("redirect_uri=" + URLEncoder.encode(AuthConfiguration.REDIRECT_URI, StandardCharsets.UTF_8)), body);

      byte[] response = "{\"access_token\":\"AT\",\"refresh_token\":\"RT\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.getResponseBody().close();
    });
    tokenServer.start();

    try {
      AuthConfiguration config = new AuthConfiguration("http://localhost:8765");
      Tokens tokens = new OAuthClient(config).exchangeCode("the-code", "the-verifier");
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
      new OAuthClient(config).exchangeCode("bad", "verifier");
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("400"), e.getMessage());
    } finally {
      tokenServer.stop(0);
    }
  }
}
