/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.maven;

/**
 * Thrown when POMs are junky.
 *
 * @author Brian Pontarelli
 */
public class POMException extends RuntimeException {
  public POMException(String message) {
    super(message);
  }

  public POMException(String message, Throwable cause) {
    super(message, cause);
  }
}
