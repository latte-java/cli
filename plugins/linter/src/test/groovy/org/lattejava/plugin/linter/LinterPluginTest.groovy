/*
 * Copyright (c) 2024, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.linter

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.lattejava.dep.domain.License
import org.lattejava.cli.domain.Project
import org.lattejava.domain.Version
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.lattejava.cli.runtime.BuildFailureException
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import static org.testng.AssertJUnit.assertEquals
import static org.testng.AssertJUnit.assertFalse
import static org.testng.AssertJUnit.assertTrue
import static org.testng.AssertJUnit.fail

/**
 * Tests the Linter plugin.
 *
 * @author Daniel DeGroff
 */
class LinterPluginTest {
  public static Path projectDir

  Output output

  Project project

  LinterPlugin plugin

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("build.savant"))) {
      projectDir = Paths.get("../linter")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    plugin = new LinterPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.reportDirectory = Paths.get("test-project/build/linter-reports")

    // Clear the output directory
    FileTools.prune(plugin.settings.reportDirectory)
  }

  @Test
  void pmd() throws Exception {
    // Execute PMD
    try {
      plugin.pmd(
          minimumPriority: "MEDIUM",
          ruleSets: ["src/test/resources/pmd/ruleset.xml"]
      )
      fail("Expected PMD analysis to fail.")
    } catch (BuildFailureException e) {
      assertEquals("PMD analysis failed.", e.getMessage())
    }

    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.html")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.xml")))

    String textReport = new String(Files.readAllBytes(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertEquals("test-project/src/main/java/org/savantbuild/test/MyClass.java:29:\tUnusedPrivateField:\tAvoid unused private fields such as 'unUsed'.\n",
        textReport)

    // Perform the same request, but do not fail on violations.
    try {
      plugin.pmd(
          failOnViolations: false,
          minimumPriority: "MEDIUM",
          ruleSets: ["src/test/resources/pmd/ruleset.xml"]
      )
    } catch (BuildFailureException e) {
      fail("Did not expected PMD analysis to fail.\n" + e.getMessage())
    }

    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.html")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.xml")))

    textReport = new String(Files.readAllBytes(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertEquals("test-project/src/main/java/org/savantbuild/test/MyClass.java:29:\tUnusedPrivateField:\tAvoid unused private fields such as 'unUsed'.\n",
        textReport)
  }

  @Test
  void pmd_missing_rulesets() throws Exception {
    // Execute PMD
    try {
      plugin.pmd()
      fail("Expected PMD analysis to fail.")
    } catch (BuildFailureException e) {
      assertEquals("""You must specify one or more values for the [ruleSets] argument. It will look something like this:

pmd(ruleSets: ["src/test/resources/pmd/ruleset.xml"])""",
          e.getMessage())
    }

    // We failed, so these should not be created
    assertFalse(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.html")))
    assertFalse(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertFalse(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.xml")))
  }
}
