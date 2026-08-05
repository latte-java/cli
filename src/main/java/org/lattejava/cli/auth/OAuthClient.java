/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.*;

import org.json.simple.*;
import org.json.simple.parser.*;
import org.lattejava.cli.runtime.*;

/**
 * Exchanges an OAuth authorization code for tokens at the IdP token endpoint, authenticating as a public client using
 * the PKCE code verifier (no client secret).
 *
 * @author Brian Pontarelli
 */
public class OAuthClient {
  private final AuthConfiguration configuration;

  public OAuthClient(AuthConfiguration configuration) {
    this.configuration = configuration;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * Exchanges an authorization code for tokens.
   *
   * @param code         The authorization code captured on the loopback redirect.
   * @param codeVerifier The PKCE code verifier that matches the challenge sent on the authorize request.
   * @param redirectURI  The same loopback redirect URI that was sent on the authorize request. The IdP requires the two
   *                     to match exactly, so it must carry the ephemeral port the loopback server bound.
   * @return The tokens.
   */
  public Tokens exchangeCode(String code, String codeVerifier, String redirectURI) {
    String form = "grant_type=authorization_code" +
        "&code=" + encode(code) +
        "&redirect_uri=" + encode(redirectURI) +
        "&client_id=" + encode(AuthConfiguration.CLIENT_ID) +
        "&code_verifier=" + encode(codeVerifier);

    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(configuration.tokenEndpoint())
                                     .header("Content-Type", "application/x-www-form-urlencoded")
                                     .header("Accept", "application/json")
                                     .timeout(Duration.ofSeconds(30))
                                     .POST(HttpRequest.BodyPublishers.ofString(form))
                                     .build();

    try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
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
      throw new RuntimeFailureException("The token request failed. Message was [" + e.getMessage() + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeFailureException("The token request was interrupted.", e);
    }
  }
}
