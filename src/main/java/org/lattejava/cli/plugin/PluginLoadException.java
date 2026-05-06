/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.plugin;

/**
 * Thrown when plugin loading fails.
 *
 * @author Brian Pontarelli
 */
public class PluginLoadException extends RuntimeException {
  public PluginLoadException(String message) {
    super(message);
  }

  public PluginLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
