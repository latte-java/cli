/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.*;
import java.security.*;
import java.util.*;

import org.lattejava.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

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
