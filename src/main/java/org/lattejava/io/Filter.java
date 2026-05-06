/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io;

/**
 * Used to specify token replacements for copying.
 *
 * @author Brian Pontarelli
 */
public class Filter {
  public final String token;

  public final String value;

  public Filter(String token, String value) {
    this.token = token;
    this.value = value;
  }
}
