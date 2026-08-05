/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.net.*;
import java.nio.charset.*;

import org.lattejava.cli.runtime.*;

/**
 * Holds the resolved OAuth issuer and the hardcoded public-client settings, and builds the OAuth endpoint URLs used by
 * the {@code latte login} command.
 *
 * @author Brian Pontarelli
 */
public class AuthConfiguration {
  public static final String CLIENT_ID = "cc5f3c9e-b28e-4632-bafe-823363669820";
  public static final String DEFAULT_ISSUER = "https://auth.lattejava.org";
  public static final String SCOPES = "openid offline_access";

  private final String issuer;

  public AuthConfiguration(String issuer) {
    String resolved = (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer.trim();
    validate(resolved);
    while (resolved.endsWith("/")) {
      resolved = resolved.substring(0, resolved.length() - 1);
    }
    this.issuer = resolved;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static void validate(String issuer) {
    URI uri;
    try {
      uri = new URI(issuer);
    } catch (URISyntaxException e) {
      throw new RuntimeFailureException("Invalid issuer URL [" + issuer + "]. It must be an absolute http or https URL.", e);
    }

    String scheme = uri.getScheme();
    if (!uri.isAbsolute() || uri.getHost() == null || (!scheme.equals("http") && !scheme.equals("https"))) {
      throw new RuntimeFailureException("Invalid issuer URL [" + issuer + "]. It must be an absolute http or https URL.");
    }
  }

  /**
   * Builds the OAuth authorization request URL for the IdP login page.
   *
   * @param state         A random nonce echoed back on the redirect to defend against CSRF.
   * @param codeChallenge The base64url-encoded SHA-256 of the PKCE code verifier.
   * @param redirectURI   The loopback redirect URI, which includes the ephemeral port the loopback server bound.
   * @return The fully-formed authorize URL.
   */
  public String authorizeURL(String state, String codeChallenge, String redirectURI) {
    return issuer + "/oauth2/authorize?response_type=code" +
        "&client_id=" + encode(CLIENT_ID) +
        "&redirect_uri=" + encode(redirectURI) +
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
}
