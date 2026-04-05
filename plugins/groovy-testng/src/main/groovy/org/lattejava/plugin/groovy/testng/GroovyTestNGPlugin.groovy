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
package org.lattejava.plugin.groovy.testng

import java.nio.charset.Charset
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile

import org.lattejava.dep.domain.ArtifactID
import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.lang.Classpath
import org.lattejava.output.Output
import org.lattejava.plugin.dep.DependencyPlugin
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration

import groovy.xml.MarkupBuilder

/**
 * The Groovy TestNG plugin. The public methods on this class define the features of the plugin.
 */
class GroovyTestNGPlugin extends BaseGroovyPlugin {
  public static final String ERROR_MESSAGE = """You must create the file [~/.config/latte/plugins/org.lattejava.plugin.groovy.properties] that contains the system configuration for the Groovy plugin. This file should include the location of the GDK (groovy and groovyc) by version. These properties look like this:

  4.0=/Users/me/.local/share/groovy/4.0.31
  5.0=/Users/me/.local/share/groovy/5.0.5
"""
  public static final String JAVA_ERROR_MESSAGE = """You must create the file [~/.config/latte/plugins/org.lattejava.plugin.java.properties] that contains the system configuration for the Java system. This file should include the location of the JDK (java and javac) by version. These properties look like this:

  21=/Users/me/.local/share/java/21.0.10+7
  25=/Users/me/.local/share/java/25.0.2+10
"""

  GroovyTestNGSettings settings = new GroovyTestNGSettings()

  DependencyPlugin dependencyPlugin

  List<Path> groovyLibraries = []

  Path javaPath

  Properties javaProperties

  Properties properties

  GroovyTestNGPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    properties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "groovy", "groovy", "jar"), ERROR_MESSAGE)
    javaProperties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "java", "java", "jar"), JAVA_ERROR_MESSAGE)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
  }

  /**
   * Runs the TestNG tests. The groups are optional, but if they are specified, only the tests in those groups are run.
   * Otherwise, all the tests are run. Here is an example calling this method:
   * <p>
   * <pre>
   *   groovyTestNG.test(groups: ["unit", "integration"])
   *   groovyTestNG.test()
   * </pre>
   *
   * @param attributes The named attributes.
   */
  void test(Map<String, Object> attributes) {
    if (runtimeConfiguration.switches.booleanSwitches.contains("skipTests")) {
      output.infoln("Skipping tests")
      return
    }

    initialize()

    // Initialize the attributes if they are null
    if (!attributes) {
      attributes = [:]
    }

    Classpath classpath = dependencyPlugin.classpath {
      settings.dependencies.each { deps -> dependencies(deps) }
      groovyLibraries.each { lib -> path(location: lib) }

      // Publications are already resolved by now, therefore, we convert them to absolute paths so they won't be resolved again
      project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
      project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
    }

    Path xmlFile = buildXMLFile(attributes["groups"])

    String command = "${javaPath} ${settings.jvmArguments} ${classpath.toString("-classpath ")} org.testng.TestNG -d ${settings.reportDirectory} ${xmlFile}"
    output.debugln("Executing [%s]", command)
    Process process = command.execute(null, project.directory.toFile())
    process.consumeProcessOutput(System.out, System.err)

    int result = process.waitFor()
    Files.delete(xmlFile)
    if (result != 0) {
      fail("Build failed.")
    }
  }

  Path buildXMLFile(List<String> groups) {
    Set<String> classNames = new TreeSet<>()
    project.publications.group("test").each { publication ->
      JarFile jarFile = new JarFile(publication.file.toFile())
      jarFile.entries().each { entry ->
        if (!entry.directory && entry.name.endsWith("Test.class")) {
          classNames.add(entry.name.replace("/", ".").replace(".class", ""))
        }
      }
    }

    Path xmlFile = FileTools.createTempPath("latte", "testng.xml", true)
    BufferedWriter writer = Files.newBufferedWriter(xmlFile, Charset.forName("UTF-8"))
    MarkupBuilder xml = new MarkupBuilder(writer)
    xml.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
    xml.mkp.yieldUnescaped('<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">\n')
    xml.suite(name: "All Tests", "allow-return-values": "true", verbose: "${settings.verbosity}") {
      delegate.test(name: "All Tests") {
        if (groups != null && groups.size() > 0) {
          delegate.groups {
            delegate.run {
              groups.each { group -> delegate.include(name: group) }
            }
          }
        }
        delegate.classes {
          classNames.each { className -> delegate."class"(name: className) }
        }
      }
    }

    writer.flush()
    writer.close()
    output.debugln("TestNG XML file contents are:\n${new String(Files.readAllBytes(xmlFile), "UTF-8")}")
    return xmlFile
  }

  private void initialize() {
    if (!settings.groovyVersion) {
      fail("You must configure the Groovy version to use with the settings object. It will look something like this:\n\n" +
          "  groovyTestNG.settings.groovyVersion=\"2.1\"")
    }

    String groovyHome = properties.getProperty(settings.groovyVersion)
    if (!groovyHome) {
      fail("No GDK is configured for version [${settings.groovyVersion}].\n\n${ERROR_MESSAGE}")
    }

    Path groovyHomePath = Paths.get(groovyHome)
    if (!Files.isDirectory(groovyHomePath)) {
      fail("The GDK directory [${groovyHome}] is invalid because it doesn't exist.")
    }

    Path libPath = groovyHomePath.resolve("lib")
    if (!Files.isDirectory(libPath)) {
      fail("The GDK directory [${groovyHome}] is invalid because it is missing the lib directory. It should be located in the directory [${libPath}]")
    }

    Files.walkFileTree(libPath, new SimpleFileVisitor<Path>() {
      @Override
      FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String name = file.getFileName().toString()
        if (name.endsWith(".jar")) {
          groovyLibraries << file
        }

        return FileVisitResult.CONTINUE
      }
    })

    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  groovyTestNG.settings.javaVersion=\"1.7\"")
    }

    String javaHome = javaProperties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n${JAVA_ERROR_MESSAGE}")
    }

    javaPath = Paths.get(javaHome, "bin/java")
    if (!Files.isRegularFile(javaPath)) {
      fail("The java executable [${javaPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(javaPath)) {
      fail("The java executable [${javaPath.toAbsolutePath()}] is not executable.")
    }
  }
}
