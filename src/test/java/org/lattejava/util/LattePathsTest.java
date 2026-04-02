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
 * Tests for LattePaths XDG directory resolution and migration.
 */
public class LattePathsTest extends BaseUnitTest {

  @Test
  public void defaultPaths() {
    Path home = Path.of("/fakehome");
    LattePaths paths = new LattePaths(home, Map.of());
    assertEquals(paths.cacheDir(), home.resolve(".cache/latte"));
    assertEquals(paths.configDir(), home.resolve(".config/latte"));
  }

  @Test
  public void xdgEnvironmentOverrides() {
    Path home = Path.of("/fakehome");
    Map<String, String> env = Map.of(
        "XDG_CACHE_HOME", "/custom/cache",
        "XDG_CONFIG_HOME", "/custom/config"
    );
    LattePaths paths = new LattePaths(home, env);
    assertEquals(paths.cacheDir(), Path.of("/custom/cache/latte"));
    assertEquals(paths.configDir(), Path.of("/custom/config/latte"));
  }

  @Test
  public void xdgPartialOverrides() {
    Path home = Path.of("/fakehome");
    Map<String, String> env = Map.of("XDG_CACHE_HOME", "/custom/cache");
    LattePaths paths = new LattePaths(home, env);
    assertEquals(paths.cacheDir(), Path.of("/custom/cache/latte"));
    assertEquals(paths.configDir(), home.resolve(".config/latte"));
  }
}
