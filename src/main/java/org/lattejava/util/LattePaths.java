/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.util;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves Latte directory paths using XDG Base Directory conventions.
 *
 * <p>XDG mapping:</p>
 * <ul>
 *   <li>Cache → $XDG_CACHE_HOME/latte (default ~/.cache/latte)</li>
 *   <li>Config → $XDG_CONFIG_HOME/latte (default ~/.config/latte)</li>
 * </ul>
 *
 * @author Brian Pontarelli
 */
public class LattePaths {
  private static final LattePaths INSTANCE = new LattePaths(
      Path.of(System.getProperty("user.home")),
      System::getenv
  );

  private final Function<String, String> envLookup;

  private final Path homeDir;

  /**
   * Creates a LattePaths with an overridable home directory and environment map. Used for testing.
   *
   * @param homeDir The home directory to use instead of user.home.
   * @param env     A map of environment variables to use instead of System.getenv().
   */
  LattePaths(Path homeDir, Map<String, String> env) {
    this(homeDir, env::get);
  }

  private LattePaths(Path homeDir, Function<String, String> envLookup) {
    this.homeDir = homeDir;
    this.envLookup = envLookup;
  }

  /**
   * Returns the default singleton instance that uses the real home directory and environment.
   *
   * @return The default LattePaths instance.
   */
  public static LattePaths get() {
    return INSTANCE;
  }

  /**
   * @return The Latte cache directory: $XDG_CACHE_HOME/latte or ~/.cache/latte
   */
  public Path cacheDir() {
    return resolveXDG("XDG_CACHE_HOME", ".cache").resolve("latte");
  }

  /**
   * @return The Latte config directory: $XDG_CONFIG_HOME/latte or ~/.config/latte
   */
  public Path configDir() {
    return resolveXDG("XDG_CONFIG_HOME", ".config").resolve("latte");
  }

  private Path resolveXDG(String envVar, String defaultRelative) {
    String envValue = envLookup.apply(envVar);
    if (envValue != null && !envValue.isEmpty()) {
      return Path.of(envValue);
    }
    return homeDir.resolve(defaultRelative);
  }
}
