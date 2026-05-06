/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.domain;

/**
 * An exception that is thrown when a Version string cannot be parsed.
 *
 * @author Brian Pontarelli
 */
public class VersionException extends RuntimeException {
  public VersionException(String message) {
    super(message);
  }
}
