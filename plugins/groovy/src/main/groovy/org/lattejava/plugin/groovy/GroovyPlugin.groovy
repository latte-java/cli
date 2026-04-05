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
package org.lattejava.plugin.groovy

import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.dep.domain.ArtifactID
import org.lattejava.cli.domain.Project
import org.lattejava.io.FileSet
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.plugin.dep.DependencyPlugin
import org.lattejava.plugin.file.FilePlugin
import org.lattejava.cli.runtime.RuntimeConfiguration

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * The Groovy plugin. The public methods on this class define the features of the plugin.
 */
class GroovyPlugin extends BaseGroovyPlugin {
  public static final String ERROR_MESSAGE = """You must create the file [~/.config/latte/plugins/org.lattejava.plugin.groovy.properties] that contains the system configuration for the Groovy plugin. This file should include the location of the GDK (groovy and groovyc) by version. These properties look like this:

  4.0=/Users/me/.local/share/groovy/4.0.31
  5.0=/Users/me/.local/share/groovy/5.0.5
"""
  public static final String JAVA_ERROR_MESSAGE = """You must create the file [~/.config/latte/plugins/org.lattejava.plugin.java.properties] that contains the system configuration for the Java system. This file should include the location of the JDK (java and javac) by version. These properties look like this:

  21=/Users/me/.local/share/java/21.0.10+7
  25=/Users/me/.local/share/java/25.0.2+10
"""
  GroovyLayout layout = new GroovyLayout()

  GroovySettings settings = new GroovySettings()

  Properties properties

  Properties javaProperties

  String groovyHome

  Path groovycPath

  Path groovyDocPath

  String javaHome

  FilePlugin filePlugin

  DependencyPlugin dependencyPlugin

  GroovyPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    filePlugin = new FilePlugin(project, runtimeConfiguration, output)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
    properties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "groovy", "groovy", "jar"), ERROR_MESSAGE)
    javaProperties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "java", "java", "jar"), JAVA_ERROR_MESSAGE)
  }

  /**
   * Cleans the build directory by completely deleting it.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.clean()
   * </pre>
   */
  void clean() {
    Path buildDir = project.directory.resolve(layout.buildDirectory)
    output.infoln "Cleaning [${buildDir}]"
    FileTools.prune(buildDir)
  }

  /**
   * Compiles the main and test Java files (src/main/groovy and src/test/groovy).
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.compile()
   * </pre>
   */
  void compile() {
    compileMain()
    compileTest()
  }

  /**
   * Compiles the main Groovy files (src/main/groovy by default).
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.compileMain()
   * </pre>
   */
  void compileMain() {
    initialize()
    compile(layout.mainSourceDirectory, layout.mainBuildDirectory, settings.mainDependencies, layout.mainBuildDirectory)
    copyResources(layout.mainResourceDirectory, layout.mainBuildDirectory)
  }

  /**
   * Compiles the test Groovy files (src/test/groovy by default).
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.compileTest()
   * </pre>
   */
  void compileTest() {
    initialize()
    compile(layout.testSourceDirectory, layout.testBuildDirectory, settings.testDependencies, layout.mainBuildDirectory, layout.testBuildDirectory)
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }

  /**
   * Compiles an arbitrary source directory to an arbitrary build directory.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.compile(Paths.get("src/foo"), Paths.get("build/bar"), [[group: "compile", transitive: false, fetchSource: false]], Paths.get("additionalClasspathDirectory"))
   * </pre>
   *
   * @param sourceDirectory The source directory that contains the groovy source files.
   * @param buildDirectory The build directory to compile the groovy files to.
   * @param dependencies The dependencies of the project to include in the compile classpath.
   */
  void compile(Path sourceDirectory, Path buildDirectory, List<Map<String, Object>> dependencies, Path... additionalClasspath) {
    Path resolvedSourceDir = project.directory.resolve(sourceDirectory)
    Path resolvedBuildDir = project.directory.resolve(buildDirectory)
    Files.createDirectories(resolvedBuildDir)

    output.debugln("Looking for modified files to compile in [${resolvedSourceDir}] compared with [${resolvedBuildDir}]")

    Predicate<Path> filter = FileTools.extensionFilter(".groovy")
    Function<Path, Path> mapper = FileTools.extensionMapper(".groovy", ".class")
    List<Path> filesToCompile = FileTools.modifiedFiles(resolvedSourceDir, resolvedBuildDir, filter, mapper)
        .collect({ path -> sourceDirectory.resolve(path) })
    if (filesToCompile.isEmpty()) {
      output.infoln("Skipping compile for source directory [${sourceDirectory}]. No files need compiling")
      return
    }

    output.infoln "Compiling [${filesToCompile.size()}] Groovy classes from [${sourceDirectory}] to [${buildDirectory}]"

    String command = "${groovycPath} ${settings.indy ? '--indy' : ''} ${settings.compilerArguments} ${classpath(dependencies, additionalClasspath)} --sourcepath ${sourceDirectory} -d ${buildDirectory} ${filesToCompile.join(" ")}"
    output.debugln("Executing [${command}]")

    Process process = command.execute(["JAVA_HOME=${javaHome}", "GROOVY_HOME=${groovyHome}", "JAVA_OPTS=${settings.jvmArguments}"], project.directory.toFile())
    process.consumeProcessOutput((Appendable) System.out, System.err)
    process.waitFor()

    int exitCode = process.exitValue()
    if (exitCode != 0) {
      fail("Compilation failed")
    }
  }

  /**
   * Copies the resource files from the source directory to the build directory. This copies all of the files
   * recursively to the build directory.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.copyResources(Paths.get("src/some-resources"), Paths.get("build/output-dir"))
   * </pre>
   *
   * @param sourceDirectory The source directory that contains the files to copy.
   * @param buildDirectory The build directory to copy the files to.
   */
  void copyResources(Path sourceDirectory, Path buildDirectory) {
    if (!Files.isDirectory(project.directory.resolve(sourceDirectory))) {
      return
    }

    filePlugin.copy(to: buildDirectory) {
      fileSet(dir: sourceDirectory)
    }
  }

  /**
   * Creates the project's Groovydoc. This executes the groovydoc command and outputs the docs to the {@code layout.docDirectory}
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.document()
   * </pre>
   */
  void document() {
    initialize()

    output.infoln "Generating GroovyDoc to [${layout.docDirectory}]"

    FileSet fileSet = new FileSet(project.directory.resolve(layout.mainSourceDirectory))
    Set<String> packages = fileSet.toFileInfos()
        .stream()
        .map({ info -> info.relative.getParent().toString().replace("/", ".") })
        .collect(Collectors.toSet())

    String command = "${groovyDocPath} ${classpath(settings.mainDependencies)} ${settings.docArguments} -sourcepath ${layout.mainSourceDirectory} -d ${layout.docDirectory} -javaVersion=JAVA_${settings.javaVersion} ${packages.join(" ")}"
    output.debugln("Executing [${command}]")

    Process process = command.execute(["JAVA_HOME=${javaHome}", "GROOVY_HOME=${groovyHome}", "JAVA_OPTS=${settings.jvmArguments}"], project.directory.toFile())
    process.consumeProcessOutput((Appendable) System.out, System.err)
    process.waitFor()

    int exitCode = process.exitValue()
    if (exitCode != 0) {
      fail("Groovydoc failed")
    }
  }

  /**
   * Creates the project's Jar files. This creates four Jar files. The main Jar, main source Jar, test Jar and test
   * source Jar.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.jar()
   * </pre>
   */
  void jar() {
    initialize()

    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactFile()), layout.mainBuildDirectory)
    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactSourceFile()), layout.mainSourceDirectory, layout.mainResourceDirectory)
    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactTestFile()), layout.testBuildDirectory)
    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactTestSourceFile()), layout.testSourceDirectory, layout.testResourceDirectory)
  }

  /**
   * Creates a single Jar file by adding all of the files in the given directories.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   groovy.jar(Paths.get("foo/bar.jar"), Paths.get("src/main/groovy"), Paths.get("some-other-dir"))
   * </pre>
   *
   * @param jarFile The Jar file to create.
   * @param directories The directories to include in the Jar file.
   */
  void jar(Path jarFile, Path... directories) {
    output.infoln("Creating JAR [${jarFile}]")

    filePlugin.jar(file: jarFile) {
      directories.each { dir ->
        optionalFileSet(dir: dir)
      }
      manifest(map: settings.jarManifest)
    }
  }

  private String classpath(List<Map<String, Object>> dependenciesList, Path... additionalPaths) {
    return dependencyPlugin.classpath {
      dependenciesList.each { deps -> dependencies(deps) }
      additionalPaths.each { additionalPath -> path(location: additionalPath) }
    }.toString("-classpath ")
  }

  private void initialize() {
    if (!settings.groovyVersion) {
      fail("You must configure the Groovy version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.groovyVersion=\"2.1\"")
    }

    groovyHome = properties.getProperty(settings.groovyVersion)
    if (!groovyHome) {
      fail("No GDK is configured for version [${settings.groovyVersion}].\n\n" + ERROR_MESSAGE)
    }

    groovycPath = Paths.get(groovyHome, "bin/groovyc")
    if (!Files.isRegularFile(groovycPath)) {
      fail("The groovyc compiler [${groovycPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(groovycPath)) {
      fail("The groovyc compiler [${groovycPath.toAbsolutePath()}] is not executable.")
    }

    groovyDocPath = Paths.get(groovyHome, "bin/groovydoc")
    if (!Files.isRegularFile(groovyDocPath)) {
      fail("The groovydoc executable [${groovyDocPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(groovyDocPath)) {
      fail("The groovydoc executable [${groovyDocPath.toAbsolutePath()}] is not executable.")
    }

    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.javaVersion=\"1.7\"")
    }

    javaHome = javaProperties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n" + JAVA_ERROR_MESSAGE)
    }
  }
}
