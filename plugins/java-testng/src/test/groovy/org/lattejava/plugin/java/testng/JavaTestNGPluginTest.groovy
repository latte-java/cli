/*
 * Copyright (c) 2014-2024, Inversoft Inc., All Rights Reserved
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

    // Verify auto-detection of module build
    assertTrue(plugin.settings.moduleBuild)

    plugin.test()
    assertModuleTestsRan("org.lattejava.test.MyClassTest")
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
}
