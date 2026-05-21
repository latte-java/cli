/*
 * Copyright (c) 2024-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.linter

import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.cli.runtime.RuntimeFailureException
import org.lattejava.dep.domain.License
import org.lattejava.domain.Version
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.testng.AssertJUnit.*

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
    if (!Files.isRegularFile(projectDir.resolve("project.latte"))) {
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
    } catch (RuntimeFailureException e) {
      assertEquals("PMD analysis failed.", e.getMessage())
    }

    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.html")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.xml")))

    String textReport = new String(Files.readAllBytes(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertTrue(textReport.contains("test-project/src/main/java/org/lattejava/test/MyClass.java"))
    assertTrue(textReport.contains("UnusedPrivateField:"))
    assertTrue(textReport.contains("Avoid unused private fields such as 'unUsed'."))

    // Perform the same request, but do not fail on violations.
    try {
      plugin.pmd(
          failOnViolations: false,
          minimumPriority: "MEDIUM",
          ruleSets: ["src/test/resources/pmd/ruleset.xml"]
      )
    } catch (RuntimeFailureException e) {
      fail("Did not expected PMD analysis to fail.\n" + e.getMessage())
    }

    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.html")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertTrue(Files.exists(plugin.settings.reportDirectory.resolve("pmd-report.xml")))

    textReport = new String(Files.readAllBytes(plugin.settings.reportDirectory.resolve("pmd-report.txt")))
    assertTrue(textReport.contains("test-project/src/main/java/org/lattejava/test/MyClass.java"))
    assertTrue(textReport.contains("UnusedPrivateField:"))
    assertTrue(textReport.contains("Avoid unused private fields such as 'unUsed'."))
  }

  @Test
  void pmd_missing_rulesets() throws Exception {
    // Execute PMD
    try {
      plugin.pmd()
      fail("Expected PMD analysis to fail.")
    } catch (RuntimeFailureException e) {
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
