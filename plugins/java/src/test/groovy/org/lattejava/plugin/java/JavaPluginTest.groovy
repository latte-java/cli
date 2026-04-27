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
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.*

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

    FileTools.prune(projectDir.resolve("build/cache"))

    JavaPlugin plugin = setupPluginForTestProject("test-project")
    plugin.output.enableDebug()

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
    FileTools.prune(projectDir.resolve("build/cache"))

    JavaPlugin plugin = setupPluginForTestProject("test-module")
    plugin.output.enableDebug()

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
    FileTools.prune(projectDir.resolve("build/cache"))

    JavaPlugin plugin = setupPluginForTestProject("test-module-separate")
    plugin.output.enableDebug()

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
  void findMainClassEntryDirectoryNonModular() throws Exception {
    compileAndJarTestProject("test-project")
    Path classesDir = projectDir.resolve("test-project/build/classes/main")

    def match = JavaPlugin.findMainClassEntry([classesDir], "org.lattejava.test.MyClass")
    assertNotNull(match)
    assertEquals(match.entry, classesDir)
    assertNull(match.moduleName)
  }

  @Test
  void findMainClassEntryDirectoryWithModuleInfo() throws Exception {
    compileAndJarTestProject("test-module")
    Path classesDir = projectDir.resolve("test-module/build/classes/main")

    def match = JavaPlugin.findMainClassEntry([classesDir], "org.lattejava.test.MyClass")
    assertNotNull(match)
    assertEquals(match.entry, classesDir)
    assertEquals(match.moduleName, "org.lattejava.test")
  }

  @Test
  void findMainClassEntryJarAutomaticModule() throws Exception {
    compileAndJarTestProject("test-project")
    Path source = projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar")
    Path automaticJar = projectDir.resolve("test-project/build/jars/test-project-automatic-1.0.0.jar")
    copyJarWithAutomaticModuleName(source, automaticJar, "auto.module.name")

    def match = JavaPlugin.findMainClassEntry([automaticJar], "org.lattejava.test.MyClass")
    assertNotNull(match)
    assertEquals(match.entry, automaticJar)
    assertEquals(match.moduleName, "auto.module.name")
  }

  @Test
  void findMainClassEntryJarExplicitModule() throws Exception {
    compileAndJarTestProject("test-module")
    Path jar = projectDir.resolve("test-module/build/jars/test-module-1.0.0.jar")

    def match = JavaPlugin.findMainClassEntry([jar], "org.lattejava.test.MyClass")
    assertNotNull(match)
    assertEquals(match.entry, jar)
    assertEquals(match.moduleName, "org.lattejava.test")
  }

  @Test
  void findMainClassEntryJarNonModular() throws Exception {
    compileAndJarTestProject("test-project")
    Path jar = projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar")

    def match = JavaPlugin.findMainClassEntry([jar], "org.lattejava.test.MyClass")
    assertNotNull(match)
    assertEquals(match.entry, jar)
    assertNull(match.moduleName)
  }

  @Test
  void findMainClassEntryReturnsNullWhenMissing() throws Exception {
    compileAndJarTestProject("test-project")
    Path classesDir = projectDir.resolve("test-project/build/classes/main")

    def match = JavaPlugin.findMainClassEntry([classesDir], "com.example.Missing")
    assertNull(match)
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
  void runEnvironmentMergesAndWorkingDirectoryHonored() throws Exception {
    JavaPlugin plugin = compileAndJarTestProject("test-project")
    registerMainPublication(plugin.project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    Path workDir = Files.createTempDirectory("latte-run-workdir")

    int rc = plugin.run(
        main: "org.lattejava.test.MyClass",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath(),
        workingDirectory: workDir,
        environment: ["LATTE_RUN_TEST": "hello-from-env"]
    )

    assertEquals(rc, 0)
    String contents = Files.readString(marker)
    assertTrue(contents.contains("env.LATTE_RUN_TEST=hello-from-env"), "Expected merged env var; got:\n" + contents)
    assertTrue(contents.contains("env.PATH.present=true"), "Expected inherited env to still be present; got:\n" + contents)
    assertTrue(contents.contains("pwd=" + workDir.toRealPath().toString()), "Expected pwd to match workingDirectory; got:\n" + contents)
  }

  @Test
  void runFailOnErrorFalseReturnsExitCode() throws Exception {
    JavaPlugin plugin = compileAndJarTestProject("test-project")
    registerMainPublication(plugin.project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    int rc = plugin.run(
        main: "org.lattejava.test.MyClass",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath() + " -Dlatte.run.exitCode=42",
        failOnError: false
    )

    assertEquals(rc, 42, "Expected the child's exit code to be returned verbatim")
    assertTrue(Files.isRegularFile(marker), "Marker should still be written before System.exit(42)")
  }

  @Test
  void runFailOnErrorTrueFailsTheBuild() throws Exception {
    JavaPlugin plugin = compileAndJarTestProject("test-project")
    registerMainPublication(plugin.project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    try {
      plugin.run(
          main: "org.lattejava.test.MyClass",
          jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath() + " -Dlatte.run.exitCode=7"
      )
      fail("Expected run() to fail the build on non-zero exit")
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("exit code [7]"), "Unexpected error message: " + expected.getMessage())
    }
  }

  @Test
  void runFailsWhenMainMissing() throws Exception {
    JavaPlugin plugin = setupPluginForTestProject("test-project")

    try {
      plugin.run([:])
      fail("Expected run() to fail when [main] attribute is missing")
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("[main]"), "Unexpected error message: " + expected.getMessage())
    }
  }

  @Test
  void runInitializeResolvesJavaPath() throws Exception {
    JavaPlugin plugin = setupPluginForTestProject("test-project")

    assertNull(plugin.javaPath)

    // initialize() is private; trigger it by calling a public method that uses it.
    // compileMain() calls initialize() via compileInternal(). We just need initialize to run;
    // a fresh compile will exit early if there are no files, which is fine here.
    plugin.clean()
    plugin.compileMain()

    assertNotNull(plugin.javaPath, "Expected initialize() to resolve javaPath")
    assertTrue(Files.isRegularFile(plugin.javaPath), "Expected javaPath [" + plugin.javaPath + "] to exist")
    assertTrue(Files.isExecutable(plugin.javaPath), "Expected javaPath [" + plugin.javaPath + "] to be executable")
  }

  @Test
  void runClassNameClasspathBuild() throws Exception {
    JavaPlugin plugin = compileAndJarTestProject("test-project")
    plugin.output.enableDebug()

    // Register the built main JAR as a publication so run() can find it.
    registerMainPublication(plugin.project, projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"))

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    int rc = plugin.run(
        main: "org.lattejava.test.MyClass",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
    )

    assertEquals(rc, 0)
    assertTrue(Files.isRegularFile(marker))
    String contents = Files.readString(marker)
    assertTrue(contents.contains("pwd="))
  }

  @Test
  void runClassNameModuleBuild() throws Exception {
    JavaPlugin plugin = compileAndJarTestProject("test-module")
    plugin.output.enableDebug()

    registerMainPublication(plugin.project, projectDir.resolve("test-module/build/jars/test-module-1.0.0.jar"))

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    int rc = plugin.run(
        main: "org.lattejava.test.MyClass",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
    )

    assertEquals(rc, 0)
    assertTrue(Files.isRegularFile(marker))
  }

  @Test
  void runClassNameNotFoundFails() throws Exception {
    JavaPlugin plugin = setupPluginForTestProject("test-project")

    try {
      plugin.run(main: "com.example.DoesNotExist")
      fail("Expected run() to fail when main class is not found")
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("Main class [com.example.DoesNotExist] was not found"),
          "Unexpected error message: " + expected.getMessage())
    }
  }

  @Test
  void runSourceFileMissingFails() throws Exception {
    JavaPlugin plugin = setupPluginForTestProject("test-project")

    try {
      plugin.run(main: "src/main/tools/DoesNotExist.java")
      fail("Expected run() to fail for a missing source file")
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("does not exist or is not readable"),
          "Unexpected error message: " + expected.getMessage())
    }
  }

  @Test
  void runSourceFileSucceeds() throws Exception {
    JavaPlugin plugin = setupPluginForTestProject("test-project")

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    int rc = plugin.run(
        main: "src/main/tools/Hello.java",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
    )

    assertEquals(rc, 0, "Expected java to exit 0")
    assertTrue(Files.isRegularFile(marker), "Expected marker file [" + marker + "] to be written")
    assertEquals(Files.readString(marker), "hello from source file")
  }

  @Test
  void runSourceFileWithImportModuleSucceeds() throws Exception {
    JavaPlugin plugin = setupPluginForTestProject("test-import-module")
    plugin.output.enableDebug()

    Path marker = Files.createTempFile("latte-run", ".txt")
    Files.deleteIfExists(marker)

    int rc = plugin.run(
        main: "src/main/java/Main.java",
        jvmArguments: "-Dlatte.run.marker=" + marker.toAbsolutePath()
    )

    assertEquals(rc, 0, "Expected java to exit 0")
    assertTrue(Files.isRegularFile(marker), "Expected marker file [" + marker + "] to be written")
    assertEquals(Files.readString(marker),
        '{"greeting":"hello from import module","module":"com.fasterxml.jackson.databind"}')
  }

  @Test
  void testModuleBuildWithoutMainModuleBuildFails() throws Exception {
    FileTools.prune(projectDir.resolve("build/cache"))

    JavaPlugin plugin = setupPluginForTestProject("test-project")
    plugin.output.enableDebug()

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

  private static JavaPlugin compileAndJarTestProject(String projectName) {
    JavaPlugin plugin = setupPluginForTestProject(projectName)
    FileTools.prune(projectDir.resolve("build/cache"))
    plugin.clean()
    plugin.compileMain()
    plugin.jar()
    return plugin
  }

  private static JavaPlugin setupPluginForTestProject(String projectName) {
    def cacheDir = projectDir.resolve("build/cache")

    Output output = new SystemOutOutput(true)

    Project project = new Project(projectDir.resolve(projectName), output)
    project.group = "org.lattejava.test"
    project.name = projectName
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))
    if (projectName == "test-module") {
      project.dependencies = new Dependencies(
          new DependencyGroup("compile", false,
              new Artifact("com.fasterxml.jackson.core:jackson-annotations:2.13.4:jar"),
              new Artifact("com.fasterxml.jackson.core:jackson-core:2.13.4:jar"),
              new Artifact("com.fasterxml.jackson.core:jackson-databind:2.13.4:jar")),
          new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    } else if (projectName == "test-import-module") {
      project.dependencies = new Dependencies(
          new DependencyGroup("compile", false,
              new Artifact("com.fasterxml.jackson.core:jackson-databind:2.13.4:jar")))
    } else {
      project.dependencies = new Dependencies(
          new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:7.12.0:jar")))
    }
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
    if (projectName == "test-project") {
      plugin.settings.libraryDirectories.add(projectDir.resolve("lib"))
    }
    if (projectName == "test-import-module") {
      // No module-info.java in this project, so auto-detection would set moduleBuild=false.
      // Force it on (mirroring the web template) so deps land on --module-path and the
      // source-file run resolves `import module` against them.
      plugin.settings.moduleBuild = true
    }
    return plugin
  }

  private static void copyJarWithAutomaticModuleName(Path source, Path destination, String moduleName) throws Exception {
    Files.deleteIfExists(destination)
    try (JarFile input = new JarFile(source.toFile())) {
      Manifest manifest = input.manifest != null ? new Manifest(input.manifest) : new Manifest()
      manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
      manifest.mainAttributes.putValue("Automatic-Module-Name", moduleName)
      try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(destination), manifest)) {
        Enumeration<JarEntry> entries = input.entries()
        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement()
          if (entry.name == JarFile.MANIFEST_NAME) {
            continue
          }
          output.putNextEntry(new JarEntry(entry.name))
          input.getInputStream(entry).transferTo(output)
          output.closeEntry()
        }
      }
    }
  }

  private static void registerMainPublication(Project project, Path jarPath) throws Exception {
    Artifact artifact = new Artifact(project.group + ":" + project.name + ":" + project.version.toString() + ":jar")
    ArtifactMetaData metaData = new ArtifactMetaData(null, project.licenses)
    Publication publication = new Publication(artifact, metaData, jarPath, null)
    project.publications.add("main", publication)
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
