/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.nio.charset.*;
import java.util.*;

import org.json.simple.*;
import org.json.simple.parser.*;
import org.lattejava.cli.runtime.*;

/**
 * Reads claims from a JWT by base64url-decoding its payload segment. Does not verify the signature; the token is
 * received directly from the IdP over TLS during login, so verification would be redundant.
 *
 * @author Brian Pontarelli
 */
public final class JWTs {
  private JWTs() {
  }

  public static String claim(String jwt, String name) {
    String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      throw new RuntimeFailureException("Malformed JWT [" + jwt + "]");
    }

    try {
      byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
      Object parsed = new JSONParser().parse(new String(payload, StandardCharsets.UTF_8));
      if (!(parsed instanceof JSONObject json)) {
        throw new RuntimeFailureException("The JWT payload was not a JSON object [" + parsed + "]");
      }
      Object value = json.get(name);
      return value == null ? null : value.toString();
    } catch (IllegalArgumentException | ParseException e) {
      throw new RuntimeFailureException("Could not decode the JWT payload. Message was [" + e.getMessage() + "]", e);
    }
  }
}
