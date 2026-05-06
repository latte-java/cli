/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

/**
 * Thrown from the project script when the runtime fails.
 *
 * @author Brian Pontarelli
 */
public class RuntimeFailureException extends RuntimeException {
  public RuntimeFailureException() {
    super();
  }

  public RuntimeFailureException(String message) {
    super(message);
  }

  public RuntimeFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
