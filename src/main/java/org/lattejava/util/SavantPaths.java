/*
 * Copyright (c) 2026, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import org.lattejava.output.Output;

/**
 * Resolves Savant directory paths using XDG Base Directory conventions and handles migration from the legacy ~/.savant/
 * directory layout.
 *
 * <p>XDG mapping:</p>
 * <ul>
 *   <li>Cache → $XDG_CACHE_HOME/savant (default ~/.cache/savant)</li>
 *   <li>Config → $XDG_CONFIG_HOME/savant (default ~/.config/savant)</li>
 * </ul>
 *
 * @author Brian Pontarelli
 */
public class SavantPaths {
  private static final SavantPaths INSTANCE = new SavantPaths(
      Path.of(System.getProperty("user.home")),
      System::getenv
  );

  private final Function<String, String> envLookup;

  private final Path homeDir;

  // This cannot be static because we need the ability to reset it in tests. However, it is effectively static outside
  // of tests because all the constructors are private, which means that only the static get() method is usable, which
  // returns a static instance.
  private volatile boolean migrated;

  /**
   * Creates a SavantPaths with an overridable home directory and environment map. Used for testing.
   *
   * @param homeDir The home directory to use instead of user.home.
   * @param env     A map of environment variables to use instead of System.getenv().
   */
  SavantPaths(Path homeDir, Map<String, String> env) {
    this(homeDir, env::get);
  }

  private SavantPaths(Path homeDir, Function<String, String> envLookup) {
    this.homeDir = homeDir;
    this.envLookup = envLookup;
  }

  /**
   * Returns the default singleton instance that uses the real home directory and environment.
   *
   * @return The default SavantPaths instance.
   */
  public static SavantPaths get() {
    return INSTANCE;
  }

  /**
   * @return The Savant cache directory: $XDG_CACHE_HOME/savant or ~/.cache/savant
   */
  public Path cacheDir() {
    return resolveXDG("XDG_CACHE_HOME", ".cache").resolve("savant");
  }

  /**
   * @return The Savant config directory: $XDG_CONFIG_HOME/savant or ~/.config/savant
   */
  public Path configDir() {
    return resolveXDG("XDG_CONFIG_HOME", ".config").resolve("savant");
  }

  /**
   * Migrates from the legacy ~/.savant/ directory to XDG locations. This method is idempotent — it only runs once per
   * instance.
   *
   * <p>Migration rules:</p>
   * <ul>
   *   <li>If XDG dirs exist AND ~/.savant exists → warn that ~/.savant is being ignored</li>
   *   <li>If XDG dirs don't exist AND ~/.savant exists → move contents to XDG locations</li>
   *   <li>If neither exists → no-op (fresh install)</li>
   * </ul>
   *
   * @param output The output for printing migration messages.
   */
  public void migrate(Output output) {
    if (migrated) {
      return;
    }
    migrated = true;

    Path dotSavant = homeDir.resolve(".savant");
    if (!Files.exists(dotSavant)) {
      return;
    }

    boolean xdgExists = Files.exists(cacheDir()) || Files.exists(configDir());
    if (xdgExists) {
      output.warningln("~/.savant directory found but XDG directories already exist. ~/.savant is being ignored and can be safely removed.");
      return;
    }

    // Migrate contents to XDG locations
    try {
      Path dotSavantCache = dotSavant.resolve("cache");
      if (Files.exists(dotSavantCache)) {
        Files.createDirectories(cacheDir().getParent());
        Files.move(dotSavantCache, cacheDir());
      }

      Path dotSavantPlugins = dotSavant.resolve("plugins");
      if (Files.exists(dotSavantPlugins)) {
        Files.createDirectories(configDir());
        Files.move(dotSavantPlugins, configDir().resolve("plugins"));
      }

      Path dotSavantConfig = dotSavant.resolve("config.properties");
      if (Files.exists(dotSavantConfig)) {
        Files.createDirectories(configDir());
        Files.move(dotSavantConfig, configDir().resolve("config.properties"));
      }

      // Delete ~/.savant if empty (or has only empty dirs)
      deleteIfEmpty(dotSavant);

      output.infoln("Migrated ~/.savant to XDG directories");
    } catch (IOException e) {
      output.errorln("Failed to fully migrate ~/.savant to XDG directories: %s", e.getMessage());
      output.errorln("Partial migration may have occurred. Move remaining files manually and delete ~/.savant.");
    }
  }

  private void deleteIfEmpty(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (var entries = Files.list(dir)) {
      for (Path entry : entries.toList()) {
        if (Files.isDirectory(entry)) {
          deleteIfEmpty(entry);
          if (Files.exists(entry)) {
            return; // Subdirectory was not empty, so neither is this one
          }
        } else {
          return; // Has a file, not empty
        }
      }
    }
    Files.delete(dir);
  }

  private Path resolveXDG(String envVar, String defaultRelative) {
    String envValue = envLookup.apply(envVar);
    if (envValue != null && !envValue.isEmpty()) {
      return Path.of(envValue);
    }
    return homeDir.resolve(defaultRelative);
  }
}
