/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.*;
import java.nio.charset.*;

import org.lattejava.*;
import org.lattejava.cli.runtime.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

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

    String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
    assertTrue(decoded.contains("response_type=code"), decoded);
    assertTrue(decoded.contains("client_id=" + AuthConfiguration.CLIENT_ID), decoded);
    assertTrue(decoded.contains("redirect_uri=http://localhost:8888/callback"), decoded);
    assertTrue(decoded.contains("scope=openid offline_access"), decoded);
    assertTrue(decoded.contains("code_challenge=the-challenge"), decoded);
    assertTrue(decoded.contains("code_challenge_method=S256"), decoded);
    assertTrue(decoded.contains("state=the-state"), decoded);
  }

  @Test
  public void defaultsIssuerWhenNullOrBlank() {
    assertEquals(new AuthConfiguration(null).issuer(), "https://auth.lattejava.org");
    assertEquals(new AuthConfiguration("   ").issuer(), "https://auth.lattejava.org");
  }

  @Test
  public void rejectsInvalidIssuer() {
    for (String bad : new String[]{"bad-url", "ftp://example.com", "http://", "://nohost", "not a url"}) {
      try {
        new AuthConfiguration(bad);
        fail("Should have rejected issuer [" + bad + "]");
      } catch (RuntimeFailureException e) {
        assertTrue(e.getMessage().contains("[" + bad + "]"), e.getMessage());
      }
    }
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
