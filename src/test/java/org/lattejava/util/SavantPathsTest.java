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

import org.lattejava.BaseUnitTest;
import org.lattejava.output.SystemOutOutput;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for SavantPaths XDG directory resolution and migration.
 */
public class SavantPathsTest extends BaseUnitTest {

  @Test
  public void defaultPaths() {
    Path home = Path.of("/fakehome");
    SavantPaths paths = new SavantPaths(home, Map.of());
    assertEquals(paths.cacheDir(), home.resolve(".cache/savant"));
    assertEquals(paths.configDir(), home.resolve(".config/savant"));
  }

  @Test
  public void xdgEnvironmentOverrides() {
    Path home = Path.of("/fakehome");
    Map<String, String> env = Map.of(
        "XDG_CACHE_HOME", "/custom/cache",
        "XDG_CONFIG_HOME", "/custom/config"
    );
    SavantPaths paths = new SavantPaths(home, env);
    assertEquals(paths.cacheDir(), Path.of("/custom/cache/savant"));
    assertEquals(paths.configDir(), Path.of("/custom/config/savant"));
  }

  @Test
  public void xdgPartialOverrides() {
    Path home = Path.of("/fakehome");
    Map<String, String> env = Map.of("XDG_CACHE_HOME", "/custom/cache");
    SavantPaths paths = new SavantPaths(home, env);
    assertEquals(paths.cacheDir(), Path.of("/custom/cache/savant"));
    assertEquals(paths.configDir(), home.resolve(".config/savant"));
  }

  @Test
  public void migrateFromDotSavant() throws IOException {
    Path tempDir = Files.createTempDirectory("savant-paths-test");
    try {
      // Set up ~/.savant structure
      Path dotSavant = tempDir.resolve(".savant");
      Files.createDirectories(dotSavant.resolve("cache/org/example"));
      Files.writeString(dotSavant.resolve("cache/org/example/artifact.jar"), "cached-artifact");
      Files.createDirectories(dotSavant.resolve("plugins"));
      Files.writeString(dotSavant.resolve("plugins/org.lattejava.plugin.java.properties"), "javaHome=/usr/lib/jvm");
      Files.writeString(dotSavant.resolve("config.properties"), "svn.username=user");

      SavantPaths paths = new SavantPaths(tempDir, Map.of());
      SystemOutOutput output = new SystemOutOutput(false);
      paths.migrate(output);

      // Verify files moved to XDG locations
      assertTrue(Files.exists(paths.cacheDir().resolve("org/example/artifact.jar")));
      assertEquals(Files.readString(paths.cacheDir().resolve("org/example/artifact.jar")), "cached-artifact");
      assertTrue(Files.exists(paths.configDir().resolve("plugins/org.lattejava.plugin.java.properties")));
      assertEquals(Files.readString(paths.configDir().resolve("plugins/org.lattejava.plugin.java.properties")), "javaHome=/usr/lib/jvm");
      assertTrue(Files.exists(paths.configDir().resolve("config.properties")));
      assertEquals(Files.readString(paths.configDir().resolve("config.properties")), "svn.username=user");

      // Verify old directory is gone
      assertFalse(Files.exists(dotSavant));
    } finally {
      deleteRecursive(tempDir);
    }
  }

  @Test
  public void migrateWarningWhenBothExist() throws IOException {
    Path tempDir = Files.createTempDirectory("savant-paths-test");
    try {
      // Set up ~/.savant
      Path dotSavant = tempDir.resolve(".savant");
      Files.createDirectories(dotSavant.resolve("cache"));
      Files.writeString(dotSavant.resolve("config.properties"), "old=value");

      // Set up XDG dirs (already exist)
      SavantPaths paths = new SavantPaths(tempDir, Map.of());
      Files.createDirectories(paths.cacheDir());

      SystemOutOutput output = new SystemOutOutput(false);
      paths.migrate(output);

      // Verify ~/.savant is NOT deleted (left alone with warning)
      assertTrue(Files.exists(dotSavant));
      // Verify old config was NOT moved
      assertTrue(Files.exists(dotSavant.resolve("config.properties")));
    } finally {
      deleteRecursive(tempDir);
    }
  }

  @Test
  public void migrateFreshInstall() throws IOException {
    Path tempDir = Files.createTempDirectory("savant-paths-test");
    try {
      // Neither ~/.savant nor XDG dirs exist
      SavantPaths paths = new SavantPaths(tempDir, Map.of());
      SystemOutOutput output = new SystemOutOutput(false);
      paths.migrate(output);

      // No errors, no directories created by migration itself
      assertFalse(Files.exists(tempDir.resolve(".savant")));
    } finally {
      deleteRecursive(tempDir);
    }
  }

  @Test
  public void migratePartialDotSavant() throws IOException {
    Path tempDir = Files.createTempDirectory("savant-paths-test");
    try {
      // Set up ~/.savant with only cache (no plugins or config.properties)
      Path dotSavant = tempDir.resolve(".savant");
      Files.createDirectories(dotSavant.resolve("cache/org/example"));
      Files.writeString(dotSavant.resolve("cache/org/example/artifact.jar"), "cached");

      SavantPaths paths = new SavantPaths(tempDir, Map.of());
      SystemOutOutput output = new SystemOutOutput(false);
      paths.migrate(output);

      // Verify cache moved
      assertTrue(Files.exists(paths.cacheDir().resolve("org/example/artifact.jar")));
      // Verify old directory is gone
      assertFalse(Files.exists(dotSavant));
    } finally {
      deleteRecursive(tempDir);
    }
  }

  private void deleteRecursive(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var entries = Files.list(path)) {
        for (Path entry : entries.toList()) {
          deleteRecursive(entry);
        }
      }
    }
    Files.deleteIfExists(path);
  }
}
