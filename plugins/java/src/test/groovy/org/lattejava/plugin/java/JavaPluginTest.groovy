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
package org.lattejava.plugin.java

import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.dep.domain.Artifact
import org.lattejava.dep.domain.Dependencies
import org.lattejava.dep.domain.DependencyGroup
import org.lattejava.dep.domain.License
import org.lattejava.dep.workflow.FetchWorkflow
import org.lattejava.dep.workflow.PublishWorkflow
import org.lattejava.dep.workflow.Workflow
import org.lattejava.dep.workflow.process.CacheProcess
import org.lattejava.dep.workflow.process.MavenProcess
import org.lattejava.domain.Version
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream

import static org.testng.Assert.*

/**
 * Tests the Java plugin.
 *
 * @author Brian Pontarelli
 */
class JavaPluginTest {
  public static Path projectDir

  @BeforeSuite
  void beforeSuite() {
    println "Setup"
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("project.latte"))) {
      projectDir = Paths.get("../java")
    }
  }

  @Test
  void all() throws Exception {
    println "Start"

    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

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

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"
    plugin.settings.libraryDirectories.add(projectDir.resolve("lib"))

    assertTrue(Paths.get(plugin.javaHome, "bin", "java").toFile().exists(),
        "Expected javaHome getter to return the directory containing bin/java")

    plugin.clean()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build")))

    plugin.compileMain()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/main/org/lattejava/test/MyClass.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/main/main.txt")))

    plugin.compileTest()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/test/org/lattejava/test/MyClassTest.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/test/test.txt")))

    plugin.jar()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"), "org/lattejava/test/MyClass.class", "main.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"), "org/lattejava/test/MyClass.class", projectDir.resolve("test-project/build/classes/main/org/lattejava/test/MyClass.class"))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar"), "org/lattejava/test/MyClass.java", "main.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar"), "org/lattejava/test/MyClass.java", projectDir.resolve("test-project/src/main/java/org/lattejava/test/MyClass.java"))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar"), "org/lattejava/test/MyClassTest.class", "test.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar"), "org/lattejava/test/MyClassTest.class", projectDir.resolve("test-project/build/classes/test/org/lattejava/test/MyClassTest.class"))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar"), "org/lattejava/test/MyClassTest.java", "test.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar"), "org/lattejava/test/MyClassTest.java", projectDir.resolve("test-project/src/test/java/org/lattejava/test/MyClassTest.java"))

    plugin.document()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/doc/index.html")))

    // Smokescreen
    plugin.printJDKModuleDeps()
  }

  @Test
  void moduleBuild() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-module"), output)
    project.group = "org.lattejava.test"
    project.name = "test-module"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

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

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"

    plugin.clean()
    assertFalse(Files.isDirectory(projectDir.resolve("test-module/build")))
    // Lazy init ran on the clean() call and auto-detected moduleBuild from module-info.java.
    assertTrue(plugin.settings.moduleBuild)

    plugin.compileMain()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module/build/classes/main/module-info.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module/build/classes/main/org/lattejava/test/MyClass.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module/build/classes/main/main.txt")))

    plugin.compileTest()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module/build/classes/test/org/lattejava/test/MyClassTest.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module/build/classes/test/test.txt")))

    plugin.jar()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module/build/jars/test-module-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-module/build/jars/test-module-1.0.0.jar"), "module-info.class", "org/lattejava/test/MyClass.class", "main.txt")
  }

  @Test
  void moduleBuildSeparate() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-module-separate"), output)
    project.group = "org.lattejava.test"
    project.name = "test-module-separate"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

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

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"

    plugin.clean()
    assertFalse(Files.isDirectory(projectDir.resolve("test-module-separate/build")))
    // Lazy init ran on the clean() call and auto-detected both flags from the module-info.java files.
    assertTrue(plugin.settings.moduleBuild)
    assertTrue(plugin.settings.testModuleBuild)

    plugin.compileMain()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/main/module-info.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/main/org/lattejava/test/MyClass.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/main/main.txt")))

    plugin.compileTest()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/test/module-info.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/test/org/lattejava/test/tests/MyClassTest.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/classes/test/test.txt")))

    plugin.jar()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/jars/test-module-separate-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-module-separate/build/jars/test-module-separate-1.0.0.jar"), "module-info.class", "org/lattejava/test/MyClass.class", "main.txt")
    assertTrue(Files.isRegularFile(projectDir.resolve("test-module-separate/build/jars/test-module-separate-test-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-module-separate/build/jars/test-module-separate-test-1.0.0.jar"), "module-info.class", "org/lattejava/test/tests/MyClassTest.class", "test.txt")
  }

  @Test
  void autoDetectRespectsLayout() throws Exception {
    Output output = new SystemOutOutput(true)

    // Fresh plugin: both flags are null (pending lazy init) regardless of project contents.
    Project project = new Project(projectDir.resolve("test-project"), output)
    JavaPlugin defaultPlugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    assertNull(defaultPlugin.settings.moduleBuild)
    assertNull(defaultPlugin.settings.testModuleBuild)

    // Override the layout BEFORE any method call so init() observes the new paths. Points
    // layout at test-module-separate's source dirs, which do contain both module-info.java files.
    defaultPlugin.layout.mainSourceDirectory = Paths.get("../test-module-separate/src/main/java")
    defaultPlugin.layout.testSourceDirectory = Paths.get("../test-module-separate/src/test/java")

    // Trigger init() via clean(). The layout override is honored because init runs lazily.
    defaultPlugin.clean()
    assertTrue(defaultPlugin.settings.moduleBuild)
    assertTrue(defaultPlugin.settings.testModuleBuild)

    // Explicit override (non-null) also wins: init does not overwrite.
    Project project2 = new Project(projectDir.resolve("test-module-separate"), output)
    JavaPlugin explicitPlugin = new JavaPlugin(project2, new RuntimeConfiguration(), output)
    explicitPlugin.settings.moduleBuild = false
    explicitPlugin.settings.testModuleBuild = false
    explicitPlugin.clean()
    assertFalse(explicitPlugin.settings.moduleBuild)
    assertFalse(explicitPlugin.settings.testModuleBuild)
  }

  @Test
  void jarjar() {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.dependencies = new Dependencies(new DependencyGroup("jarjar", false, new Artifact("com.amazonaws:aws-java-sdk-ec2:1.12.243:jar")))
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
    project.workflow.mappings.put("com.fasterxml.jackson.core:jackson-databind:2.12.6.1", new Version("2.12.6+1"))

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.clean()
    plugin.jarjar(dependencyGroup: "jarjar", outputDirectory: "build/classes/main") {
      rule(from: "org.**", to: "shaded.org.@1")
      rule(from: "com.fasterxml.**", to: "shaded.com.fasterxml.@1")
    }

    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/com/amazonaws")))
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/shaded/com/fasterxml")))
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/shaded/org/apache")))
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/shaded/org/joda")))

    // Make sure they were shaded
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/com/fasterxml")))
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/org/apache")))
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/classes/main/org/joda")))
  }

  @Test
  void testModuleBuildWithoutMainModuleBuildFails() throws Exception {
    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.lattejava.test"
    project.name = "test-project"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

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

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "25"
    plugin.settings.libraryDirectories.add(projectDir.resolve("lib"))

    // Force the invalid combination: test module on, main module off
    plugin.settings.moduleBuild = false
    plugin.settings.testModuleBuild = true

    plugin.clean()
    plugin.compileMain()

    try {
      plugin.compileTest()
      fail("Expected compileTest() to fail when testModuleBuild is true but moduleBuild is false")
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("testModuleBuild is enabled but moduleBuild is not"),
          "Unexpected error message: " + expected.getMessage())
    }
  }

  private static void assertJarContains(Path jarFile, String... entries) {
    JarFile jf = new JarFile(jarFile.toFile())
    entries.each({ entry -> assertNotNull(jf.getEntry(entry), "Jar [${jarFile}] is missing entry [${entry}]") })
    jf.close()
  }

  private static void assertJarFileEquals(Path jarFile, String entry, Path original) throws IOException {
    JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile))
    JarEntry jarEntry = jis.getNextJarEntry()
    while (jarEntry != null && jarEntry.getName() != entry) {
      jarEntry = jis.getNextJarEntry()
    }

    if (jarEntry == null) {
      fail("Jar [" + jarFile + "] is missing entry [" + entry + "]")
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    byte[] buf = new byte[1024]
    int length
    while ((length = jis.read(buf)) != -1) {
      baos.write(buf, 0, length)
    }

    println Files.getLastModifiedTime(original)
    assertEquals(Files.readAllBytes(original), baos.toByteArray())
    assertEquals((long) jarEntry.getSize(), (long) Files.size(original))
    assertEquals(jarEntry.getCreationTime(), Files.getAttribute(original, "creationTime"))
//    assertEquals(jarEntry.getLastModifiedTime(), Files.getLastModifiedTime(original));
//    assertEquals(jarEntry.getTime(), Files.getLastModifiedTime(original).toMillis());
  }
}
