/*
 * Copyright (c) 2014-2026, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.java.testng

import groovy.xml.XmlSlurper
import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.cli.runtime.RuntimeFailureException
import org.lattejava.dep.domain.*
import org.lattejava.dep.workflow.FetchWorkflow
import org.lattejava.dep.workflow.PublishWorkflow
import org.lattejava.dep.workflow.Workflow
import org.lattejava.dep.workflow.process.CacheProcess
import org.lattejava.dep.workflow.process.MavenProcess
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

import static java.util.Arrays.asList
import static org.testng.Assert.*

/**
 * Tests the Java TestNG plugin.
 *
 * <strong>NOTE</strong>: This repository has the class files and JARs for the test-project checked in. This is
 * on purpose so that we don't have to compile the test-project. These need to be updated to lattejava at some point
 *
 * @author Brian Pontarelli
 */
class JavaTestNGPluginTest {
  public static Path projectDir

  Output output

  Project project

  @BeforeSuite
  void beforeSuite() {
    projectDir = Paths.get("")
    if (Files.isDirectory(projectDir.resolve("plugins"))) {
      projectDir = Paths.get("plugins/java-testng")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)
    FileTools.prune(projectDir.resolve("test-project/build/test-reports"))

    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.publications.add("main", new Publication(new ReifiedArtifact("org.lattejava.test:test-project:1.0.0", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        project.directory.resolve("build/jars/test-project-1.0.0.jar"), null))
    project.publications.add("test", new Publication(new ReifiedArtifact("org.lattejava.test:test-project:test-project-test:1.0.0:jar", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        project.directory.resolve("build/jars/test-project-test-1.0.0.jar"), null))

    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString(), cacheDir.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString(), cacheDir.toString())
        ),
        output
    )
  }

  @Test
  void moduleBuild() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)
    FileTools.prune(projectDir.resolve("test-module/build/test-reports"))

    Output moduleOutput = new SystemOutOutput(true)
    moduleOutput.enableDebug()

    Project moduleProject = new Project(projectDir.resolve("test-module"), moduleOutput)
    moduleProject.group = "org.lattejava.test"
    moduleProject.name = "test-module"
    moduleProject.version = new Version("1.0.0")
    moduleProject.licenses.add(License.parse("ApacheV2_0", null))

    moduleProject.publications.add("main", new Publication(new ReifiedArtifact("org.lattejava.test:test-module:1.0.0", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        moduleProject.directory.resolve("build/jars/test-module-1.0.0.jar"), null))
    moduleProject.publications.add("test", new Publication(new ReifiedArtifact("org.lattejava.test:test-module:test-module-test:1.0.0:jar", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        moduleProject.directory.resolve("build/jars/test-module-test-1.0.0.jar"), null))

    moduleProject.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    moduleProject.workflow = new Workflow(
        new FetchWorkflow(moduleOutput,
            new CacheProcess(moduleOutput, cacheDir.toString(), cacheDir.toString(), cacheDir.toString()),
            new MavenProcess(moduleOutput, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(moduleOutput, cacheDir.toString(), cacheDir.toString(), cacheDir.toString())
        ),
        moduleOutput
    )

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(moduleProject, new RuntimeConfiguration(), moduleOutput)
    plugin.settings.javaVersion = "25"

    plugin.test()
    // Lazy init ran during test() and auto-detected moduleBuild from the main module-info.java.
    assertTrue(plugin.settings.moduleBuild)
    assertModuleTestsRan("org.lattejava.test.MyClassTest")
  }

  @Test
  void moduleBuildSeparate() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)
    FileTools.prune(projectDir.resolve("test-module-separate/build/test-reports"))

    Output moduleOutput = new SystemOutOutput(true)
    moduleOutput.enableDebug()

    Project moduleProject = new Project(projectDir.resolve("test-module-separate"), moduleOutput)
    moduleProject.group = "org.lattejava.test"
    moduleProject.name = "test-module-separate"
    moduleProject.version = new Version("1.0.0")
    moduleProject.licenses.add(License.parse("ApacheV2_0", null))

    moduleProject.publications.add("main", new Publication(new ReifiedArtifact("org.lattejava.test:test-module-separate:1.0.0", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        moduleProject.directory.resolve("build/jars/test-module-separate-1.0.0.jar"), null))
    moduleProject.publications.add("test", new Publication(new ReifiedArtifact("org.lattejava.test:test-module-separate:test-module-separate-test:1.0.0:jar", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        moduleProject.directory.resolve("build/jars/test-module-separate-test-1.0.0.jar"), null))

    moduleProject.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    moduleProject.workflow = new Workflow(
        new FetchWorkflow(moduleOutput,
            new CacheProcess(moduleOutput, cacheDir.toString(), cacheDir.toString(), cacheDir.toString()),
            new MavenProcess(moduleOutput, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(moduleOutput, cacheDir.toString(), cacheDir.toString(), cacheDir.toString())
        ),
        moduleOutput
    )

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(moduleProject, new RuntimeConfiguration(), moduleOutput)
    plugin.settings.javaVersion = "25"

    plugin.test()
    // Lazy init ran during test() and auto-detected both flags from the module-info.java files.
    assertTrue(plugin.settings.moduleBuild)
    assertTrue(plugin.settings.testModuleBuild)
    assertSeparateModuleTestsRan("org.lattejava.test.tests.MyClassTest")
  }

  static void assertSeparateModuleTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-module-separate/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-module-separate/build/test-reports/latte-tests/all.xml")))

    HashSet<String> tested = findNonIgnoredTestClasses("test-module-separate/build/test-reports/latte-tests/all.xml")
    assertEquals(tested, new HashSet<>(asList(classNames)))
  }

  @Test
  void autoDetectRespectsLayout() throws Exception {
    // Use the skipTests switch so test() returns immediately after init() without actually
    // invoking javac/java — we only want to exercise the lazy auto-detection.
    RuntimeConfiguration skipConfig = new RuntimeConfiguration()
    skipConfig.switches.booleanSwitches.add("skipTests")

    // Fresh plugin: both flags are null (pending lazy init) regardless of project contents.
    JavaTestNGPlugin defaultPlugin = new JavaTestNGPlugin(project, skipConfig, output)
    assertNull(defaultPlugin.settings.moduleBuild)
    assertNull(defaultPlugin.settings.testModuleBuild)

    // test-project has no module-info.java. test() triggers init(), which records false/false.
    defaultPlugin.test()
    assertFalse(defaultPlugin.settings.moduleBuild)
    assertFalse(defaultPlugin.settings.testModuleBuild)

    // Override the layout BEFORE calling test() on a fresh plugin so init() sees the new paths.
    JavaTestNGPlugin layoutPlugin = new JavaTestNGPlugin(project, skipConfig, output)
    layoutPlugin.layout.mainSourceDirectory = Paths.get("../test-module-separate/src/main/java")
    layoutPlugin.layout.testSourceDirectory = Paths.get("../test-module-separate/src/test/java")
    layoutPlugin.test()
    assertTrue(layoutPlugin.settings.moduleBuild)
    assertTrue(layoutPlugin.settings.testModuleBuild)

    // Explicit override (non-null) wins over auto-detect: init does not overwrite.
    Project moduleProject = new Project(projectDir.resolve("test-module-separate"), output)
    JavaTestNGPlugin explicitPlugin = new JavaTestNGPlugin(moduleProject, skipConfig, output)
    explicitPlugin.settings.moduleBuild = false
    explicitPlugin.settings.testModuleBuild = false
    explicitPlugin.test()
    assertFalse(explicitPlugin.settings.moduleBuild)
    assertFalse(explicitPlugin.settings.testModuleBuild)
  }

  @Test
  void all() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"

    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    plugin.test(null)
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")
  }

  @Test
  void coverage() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"
    plugin.settings.codeCoverage = true

    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    plugin.test(null)
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    // assert our code coverage report exists
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/coverage-reports")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/coverage-reports/index.html")))
  }

  @Test
  void skipTests() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()
    runtimeConfiguration.switches.booleanSwitches.add("skipTests")

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.javaVersion = "25"

    plugin.test()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
  }

  @Test
  void singleTestSwitch() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.javaVersion = "25"

    // Simple name
    runtimeConfiguration.switches.add("test", "MyClassTest")
    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest")
    assertTestsDidNotRun("org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    // Fully qualified name
    runtimeConfiguration.switches.add("test", "org.savantbuild.test.MyClassTest")
    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest")
    assertTestsDidNotRun("org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")

    // Fuzzy
    runtimeConfiguration.switches.add("test", "MyClass")
    plugin.test()
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest", "org.savantbuild.test.MyClassUnitTest")
    assertTestsDidNotRun()
  }

  @Test
  void withExclude() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"

    plugin.test(exclude: ["unit"])
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassIntegrationTest")

    plugin.test(exclude: ["integration"])
    assertTestsRan("org.savantbuild.test.MyClassTest", "org.savantbuild.test.MyClassUnitTest")
  }

  @Test
  void withGroup() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"

    plugin.test(groups: ["unit"])
    assertTestsRan("org.savantbuild.test.MyClassUnitTest")

    plugin.test(groups: ["integration"])
    assertTestsRan("org.savantbuild.test.MyClassIntegrationTest")
  }

  static void assertModuleTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-module/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-module/build/test-reports/latte-tests/all.xml")))

    HashSet<String> tested = findNonIgnoredTestClasses("test-module/build/test-reports/latte-tests/all.xml")
    assertEquals(tested, new HashSet<>(asList(classNames)))
  }

  static void assertTestsDidNotRun(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test-reports/latte-tests/all.xml")))

    HashSet<String> tested = findNonIgnoredTestClasses("test-project/build/test-reports/latte-tests/all.xml")
    for (String className : classNames) {
      if (tested.contains(className)) {
        fail("Test [" + className + "] was not expected to run.")
      }
    }
  }

  static void assertTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test-reports/latte-tests/all.xml")))

    HashSet<String> tested = findNonIgnoredTestClasses("test-project/build/test-reports/latte-tests/all.xml")
    assertEquals(tested, new HashSet<>(asList(classNames)))
  }

  private static HashSet<String> findNonIgnoredTestClasses(String file) {
    def testsuite = new XmlSlurper().parse(projectDir.resolve(file).toFile())
    Set<String> tested = new HashSet<>()
    testsuite.testcase
        .findAll { testcase -> testcase.ignored.isEmpty() }
        .each { testcase -> tested << testcase.@classname.text() }
    return tested
  }

  @Test
  void handleExitCodeZeroDoesNotFail() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    // No throw — all tests passed.
    plugin.handleExitCode(0)
  }

  @Test
  void handleExitCodeSkippedDoesNotFail() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    // Exit code 2 means tests were skipped but none failed — not a build failure.
    plugin.handleExitCode(2)
  }

  @Test
  void handleExitCodeFailureThrows() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.handleExitCode(1)
      fail("Expected RuntimeFailureException for exit code 1")
    } catch (RuntimeFailureException ignored) {
      // good
    }
  }

  @Test
  void handleExitCodeOtherThrows() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.handleExitCode(8)
      fail("Expected RuntimeFailureException for exit code 8")
    } catch (RuntimeFailureException ignored) {
      // good
    }
  }

  @Test
  void handleExitCodeFailurePreservesResultAndFailedFiles() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)

    // Pre-stage synthetic TestNG output files that the real run would have produced.
    Path reportsDir = project.directory.resolve("build/test-reports")
    Files.createDirectories(reportsDir)
    Path results = reportsDir.resolve("testng-results.xml")
    Path failed = reportsDir.resolve("testng-failed.xml")
    Files.writeString(results, "<results/>")
    Files.writeString(failed, "<failed/>")

    // Clear any prior preserved state.
    Path lastResults = plugin.lastTestResultsPath()
    Path lastFailed = plugin.lastFailedTestsPath()
    Files.deleteIfExists(lastResults)
    Files.deleteIfExists(lastFailed)

    try {
      plugin.handleExitCode(1)
      fail("Expected RuntimeFailureException for exit code 1")
    } catch (RuntimeFailureException ignored) {
      // good
    }

    assertTrue(Files.isRegularFile(lastResults), "Expected testng-results.xml to be preserved at " + lastResults)
    assertTrue(Files.isRegularFile(lastFailed), "Expected testng-failed.xml to be preserved at " + lastFailed)
    assertEquals(Files.readString(lastResults), "<results/>")
    assertEquals(Files.readString(lastFailed), "<failed/>")
  }

  @Test
  void handleExitCodeSkippedDoesNotPreserveFiles() throws Exception {
    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, new RuntimeConfiguration(), output)

    // Pre-stage a results file; exit code 2 should NOT copy it.
    Path reportsDir = project.directory.resolve("build/test-reports")
    Files.createDirectories(reportsDir)
    Files.writeString(reportsDir.resolve("testng-results.xml"), "<results/>")

    Path lastResults = plugin.lastTestResultsPath()
    Path lastFailed = plugin.lastFailedTestsPath()
    Files.deleteIfExists(lastResults)
    Files.deleteIfExists(lastFailed)

    plugin.handleExitCode(2)

    assertFalse(Files.exists(lastResults), "Expected no testng-results.xml preserved for exit code 2")
    assertFalse(Files.exists(lastFailed), "Expected no testng-failed.xml preserved for exit code 2")
  }

  @Test
  void onlyFailedNoPriorRunDoesNothing() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()
    runtimeConfiguration.switches.booleanSwitches.add("onlyFailed")

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.javaVersion = "25"

    // Make sure no failed file exists from a prior test in the same VM.
    Files.deleteIfExists(plugin.lastFailedTestsPath())

    plugin.test()

    // Early return — no java process launched, no reports dir.
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")),
        "Expected no test-reports directory when --onlyFailed runs with no prior failure")
  }

  @Test
  void onlyFailedWithPriorFailureRunsOnlyThoseTests() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()
    runtimeConfiguration.switches.booleanSwitches.add("onlyFailed")

    JavaTestNGPlugin plugin = new JavaTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.javaVersion = "25"

    // Hand-craft a testng-failed.xml naming only MyClassTest, and place it where
    // --onlyFailed will find it.
    Path staged = plugin.lastFailedTestsPath()
    Files.deleteIfExists(staged)
    Files.createDirectories(staged.getParent())
    Files.writeString(staged, '''\
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite parallel="none" name="Failed suite" allow-return-values="true">
  <test parallel="none" name="failed(failed)">
    <classes>
      <class name="org.savantbuild.test.MyClassTest">
        <methods>
          <include name="doSomething"/>
        </methods>
      </class>
    </classes>
  </test>
</suite>
''')

    plugin.test()

    // TestNG writes testng-results.xml regardless of suite name, so parse that to verify
    // only doSomething from MyClassTest ran. (assertTestsRan reads latte-tests/all.xml,
    // but --onlyFailed feeds a "Failed suite"-named XML, so the per-suite report is
    // written under a different directory. testng-results.xml is stable.)
    Path results = projectDir.resolve("test-project/build/test-reports/testng-results.xml")
    assertTrue(Files.isRegularFile(results), "Expected testng-results.xml at " + results)
    def parsed = new XmlSlurper().parse(results.toFile())
    assertEquals(parsed.@total.text(), "1",
        "Expected exactly one test method to run under --onlyFailed, got total=" + parsed.@total.text())
    assertEquals(parsed.@passed.text(), "1")
    assertEquals(parsed.@failed.text(), "0")

    Set<String> ranClasses = new HashSet<>()
    parsed.'**'.findAll { it.name() == 'class' }.each { cls ->
      ranClasses << cls.@name.text()
    }
    assertTrue(ranClasses.contains("org.savantbuild.test.MyClassTest"))
    assertFalse(ranClasses.contains("org.savantbuild.test.MyClassIntegrationTest"))
    assertFalse(ranClasses.contains("org.savantbuild.test.MyClassUnitTest"))
  }
}
