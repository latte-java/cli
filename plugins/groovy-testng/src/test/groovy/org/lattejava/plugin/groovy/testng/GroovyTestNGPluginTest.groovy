/*
 * Copyright (c) 2014-2018, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.groovy.testng

import org.lattejava.dep.workflow.process.MavenProcess

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.lattejava.dep.domain.Artifact
import org.lattejava.dep.domain.ArtifactMetaData
import org.lattejava.dep.domain.Dependencies
import org.lattejava.dep.domain.DependencyGroup
import org.lattejava.dep.domain.License
import org.lattejava.dep.domain.Publication
import org.lattejava.dep.domain.ReifiedArtifact
import org.lattejava.dep.workflow.FetchWorkflow
import org.lattejava.dep.workflow.PublishWorkflow
import org.lattejava.dep.workflow.Workflow
import org.lattejava.dep.workflow.process.CacheProcess
import org.lattejava.dep.workflow.process.URLProcess
import org.lattejava.cli.domain.Project
import org.lattejava.domain.Version
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import groovy.xml.XmlSlurper
import static java.util.Arrays.asList
import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertTrue

/**
 * Tests the TestNG plugin.
 *
 * @author Brian Pontarelli
 */
class GroovyTestNGPluginTest {
  public static Path projectDir

  Output output

  Project project

  @BeforeSuite
  void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("build.savant"))) {
      projectDir = Paths.get("../groovy-testng")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    FileTools.prune(projectDir.resolve("build/cache"))
    FileTools.prune(projectDir.resolve("test-project/build/test-reports"))

    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.publications.add("main", new Publication(new ReifiedArtifact("org.lattejava.test:test-project:1.0.0", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        project.directory.resolve("build/jars/test-project-1.0.0.jar"), null))
    project.publications.add("test", new Publication(new ReifiedArtifact("org.lattejava.test:test-project:test-project-test:1.0.0:jar", [License.parse("Commercial", "License")]), new ArtifactMetaData(null, [License.parse("Commercial", "License")]),
        project.directory.resolve("build/jars/test-project-test-1.0.0.jar"), null))

    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:6.8.7:jar")))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, projectDir.resolve("build/cache").toString(), null, null),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, projectDir.resolve("build/cache").toString(), null, null)
        ),
        output
    )
    project.workflow.mappings.put("org.beanshell:bsh:2.0b4", new Version("2.0+b4"))
    project.workflow.mappings.put("org.beanshell:beanshell:2.0b4", new Version("2.0+b4"))
  }

  @Test
  void test() throws Exception {
    GroovyTestNGPlugin plugin = new GroovyTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.groovyVersion = "5.0"
    plugin.settings.javaVersion = "25"

    plugin.test()
    assertTestsRan("MyClassTest", "MyClassIntegrationTest", "MyClassUnitTest")

    plugin.test(null)
    assertTestsRan("MyClassTest", "MyClassIntegrationTest", "MyClassUnitTest")
  }

  @Test
  void skipTests() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()
    runtimeConfiguration.switches.booleanSwitches.add("skipTests")

    GroovyTestNGPlugin plugin = new GroovyTestNGPlugin(project, runtimeConfiguration, output)
    plugin.settings.groovyVersion = "5.0"
    plugin.settings.javaVersion = "25"

    plugin.test()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
  }

  @Test
  void withGroup() throws Exception {
    GroovyTestNGPlugin plugin = new GroovyTestNGPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.groovyVersion = "5.0"
    plugin.settings.javaVersion = "25"

    plugin.test(groups: ["unit"])
    assertTestsRan("MyClassUnitTest")

    plugin.test(groups: ["integration"])
    assertTestsRan("MyClassIntegrationTest")
  }

  static void assertTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test-reports")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml")))

    def testsuite = new XmlSlurper().parse(projectDir.resolve("test-project/build/test-reports/All Tests/All Tests.xml").toFile())
    Set<String> tested = new HashSet<>()
    testsuite.testcase.each { testcase -> tested << testcase.@classname.text() }

    assertEquals(tested, new HashSet<>(asList(classNames)))
  }
}
