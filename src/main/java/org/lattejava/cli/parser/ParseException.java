/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser;

/**
 * Thrown when parsing fails for any reason.
 *
 * @author Brian Pontarelli
 */
public class ParseException extends RuntimeException {
  public ParseException(String message) {
    super(message);
  }

  public ParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
