/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.*;
import java.security.*;
import java.util.*;

import org.lattejava.cli.runtime.*;

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
      throw new RuntimeFailureException("SHA-256 is not available in this JVM. Message was [" + e.getMessage() + "]", e);
    }
  }
}
