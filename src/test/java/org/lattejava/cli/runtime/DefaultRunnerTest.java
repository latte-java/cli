/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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
package org.lattejava.cli.runtime;

import java.nio.file.Files;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.parser.DefaultTargetGraphBuilder;
import org.lattejava.cli.parser.groovy.GroovyProjectFileParser;
import org.lattejava.dep.PathTools;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Tests the runner.
 *
 * @author Brian Pontarelli
 */
public class DefaultRunnerTest extends BaseUnitTest {
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
