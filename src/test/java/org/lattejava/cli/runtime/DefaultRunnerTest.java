/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

import java.nio.file.Files;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.command.LoginCommand;
import org.lattejava.cli.parser.DefaultTargetGraphBuilder;
import org.lattejava.cli.parser.groovy.GroovyProjectFileParser;
import org.lattejava.dep.PathTools;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the runner.
 *
 * @author Brian Pontarelli
 */
public class DefaultRunnerTest extends BaseUnitTest {
  @Test
  public void loginCommandIsRegistered() {
    assertTrue(DefaultRunner.COMMANDS.get("login") instanceof LoginCommand);
  }

  @Test
  public void javaProject() throws Exception {
    PathTools.prune(projectDir.resolve("test-project/build"));
    Files.createDirectories(projectDir.resolve("test-project/build"));

    Runner runner = new DefaultRunner(output, new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder()), new DefaultProjectRunner(output));
    runner.run(projectDir.resolve("test-project"), new RuntimeConfiguration(false, "write"));
    assertEquals(Files.readString(projectDir.resolve("test-project/build/test-file.txt")), "File contents");

    runner.run(projectDir.resolve("test-project"), new RuntimeConfiguration(true, "delete"));
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build")));
  }

  @Test
  public void javaProjectWithLicenses() throws Exception {
    PathTools.prune(projectDir.resolve("test-project-licenses/build"));
    Files.createDirectories(projectDir.resolve("test-project-licenses/build"));

    Runner runner = new DefaultRunner(output, new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder()), new DefaultProjectRunner(output));
    runner.run(projectDir.resolve("test-project-licenses"), new RuntimeConfiguration(false, "write"));
    assertEquals(Files.readString(projectDir.resolve("test-project-licenses/build/test-file.txt")), "File contents");

    runner.run(projectDir.resolve("test-project-licenses"), new RuntimeConfiguration(true, "delete"));
    assertFalse(Files.isDirectory(projectDir.resolve("test-project-licenses/build")));
  }
}
