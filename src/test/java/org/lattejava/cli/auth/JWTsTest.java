/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.*;
import java.util.*;

import org.lattejava.*;
import org.lattejava.cli.runtime.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Tests JWT payload decoding.
 *
 * @author Brian Pontarelli
 */
public class JWTsTest extends BaseUnitTest {
  private static String token(String payloadJSON) {
    String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJSON.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".signature";
  }

  @Test
  public void malformedTokenThrows() {
    try {
      JWTs.claim("not-a-jwt", "email");
      fail("Should have thrown");
    } catch (RuntimeFailureException e) {
      assertTrue(e.getMessage().contains("[not-a-jwt]"), e.getMessage());
    }
  }

  @Test
  public void missingClaimReturnsNull() {
    assertNull(JWTs.claim(token("{\"sub\":\"123\"}"), "email"));
  }

  @Test
  public void readsClaim() {
    assertEquals(JWTs.claim(token("{\"email\":\"test@lattejava.org\"}"), "email"), "test@lattejava.org");
  }
}
