/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

/**
 * Parses the command-line arguments and builds the {@link RuntimeConfiguration} instance.
 *
 * @author Brian Pontarelli
 */
public interface RuntimeConfigurationParser {
  /**
   * Parses the command-line arguments and builds a RuntimeConfiguration instance.
   *
   * @param arguments The CLI arguments.
   * @return The RuntimeConfiguration.
   */
  RuntimeConfiguration parse(String... arguments);
}
