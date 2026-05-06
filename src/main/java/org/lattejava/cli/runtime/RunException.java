/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

/**
 * Thrown when Latte fails while running the build. This is specifically during the run phase of the build where the
 * targets are invoked.
 *
 * @author Brian Pontarelli
 */
public class RunException extends RuntimeException {
  public RunException(String message) {
    super(message);
  }
}
