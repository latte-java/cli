/*
 * Copyright (c) 2013-2025, Inversoft Inc., All Rights Reserved
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

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.jacoco.agent.AgentJar
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.tools.ExecFileLoader
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.report.DirectorySourceFileLocator
import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.MultiSourceFileLocator
import org.jacoco.report.html.HTMLFormatter
import org.lattejava.dep.domain.ArtifactID
import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.lang.Classpath
import org.lattejava.output.Output
import org.lattejava.plugin.dep.DependencyPlugin
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import groovy.xml.MarkupBuilder

/**
 * The Java TestNG plugin. The public methods on this class define the features of the plugin.
 */
class JavaTestNGPlugin extends BaseGroovyPlugin {
  public static
  final String ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.lattejava.plugin.java.properties] " +
      "that contains the system configuration for the Java system. This file should include the location of the JDK " +
      "(java and javac) by version. These properties look like this:\n\n" +
      "  1.6=/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home\n" +
      "  1.7=/Library/Java/JavaVirtualMachines/jdk1.7.0_10.jdk/Contents/Home\n" +
      "  1.8=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home\n"

  DependencyPlugin dependencyPlugin

  Path javaPath

  Properties properties

  JavaTestNGSettings settings = new JavaTestNGSettings()

  JavaTestNGPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    properties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "java", "java", "jar"), ERROR_MESSAGE)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
  }

  /**
   * Runs the TestNG tests. The groups are optional, but if they are specified, only the tests in those groups are run.
   * Otherwise, all the tests are run. Here is an example calling this method:
   * <p>
   * <pre>
   *   groovyTestNG.test(groups: ["unit", "performance"])
   * </pre>
   *
   * Supported command line options:
   *  --onlyFailed    only runs test that failed on previous test run
   *  --onlyChanges   only run tests for java classes and test classes changed on this PR or branch
   *    --commitRange=<commit> override default commit range of "--no-merges origin..HEAD". Can be range or commit.
   *  --keepXML       keep the testNG XML config file (can be used in IntelliJ)
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

      // Publications are already resolved by now, therefore, we convert them to absolute paths so they won't be resolved again
      project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
      project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
    }

    Path xmlFile = buildXMLFile(attributes["groups"], attributes["exclude"])
    def jacocoArgs = getCodeCoverageArguments()
    String command = "${javaPath} ${settings.jvmArguments} ${classpath.toString("-classpath ")}${jacocoArgs} org.testng.TestNG -d ${settings.reportDirectory} ${settings.testngArguments} ${xmlFile}"
    output.debugln("Running command [%s]", command)

    // StringTokenizer handles multiple spaces, etc. for us (jvmArguments is optional)
    def args = new StringTokenizer(command).toList() as List<String>
    Process process = new ProcessBuilder(args)
    // need to use inheritIO as opposed to process.consumeProcessOutput(System.out, System.err) because that will
    // cause buffering that hides test output that does not end with a carriage return
    .inheritIO()
    .directory(project.directory.toFile())
    .start()

    int result = process.waitFor()
    if (settings.codeCoverage) {
      produceCodeCoverageReports()
    }
    if (runtimeConfiguration.switches.booleanSwitches.contains("keepXML")) {
      Path testSuite = project.directory.resolve("build/test/${xmlFile.fileName.toString()}")
      Files.copy(xmlFile, testSuite)
      output.infoln("TestNG configuration saved in: $testSuite")
    } else {
      Files.delete(xmlFile)
    }
    if (result != 0) {
      // Keep a copy of the last set of test results when there is a failure.
      Path testResults = project.directory.resolve("build/test-reports/testng-results.xml")
      if (testResults.toFile().exists()) {
        Path target = getLastTestResultsPath()
        Files.deleteIfExists(target)
        Files.createDirectories(target.getParent())
        Files.createFile(target)
        Files.copy(testResults,
            target,
            StandardCopyOption.REPLACE_EXISTING)
      }

      fail("Build failed.")
    }
  }

  Path buildXMLFile(List<String> groups, List<String> excludes) {
    Set<String> classNames = new TreeSet<>()

    if (runtimeConfiguration.switches.booleanSwitches.contains("onlyFailed")) {
      output.infoln("Retry previously failed tests.")
      File testResults = getLastTestResultsPath().toFile()
      if (testResults.exists()) {
        findFailedTests(testResults, classNames)
        List<String> dashTestFlags = new ArrayList<>()
        for (String s : classNames) {
          dashTestFlags.add("--test=" + s)
        }

        output.infoln("Found [" + classNames.size() + "] failed tests to run. Equivalent to running:\n" + String.join(" ", dashTestFlags))
      } else {
        output.infoln("No test results found from a prior test run. File not found [" + testResults.toString() + "].")
      }
    } else if (runtimeConfiguration.switches.booleanSwitches.contains("onlyChanges")) {
      output.infoln("Only running tests for changed files (⚠️ misses changes in dependencies).")

      String committedChanges
      if (runtimeConfiguration.switches.valueSwitches.containsKey("commitRange")) {
        // user specified a commit or commit range
        String commitRange = runtimeConfiguration.switches.values("commitRange").first()
        committedChanges = "git diff --name-only --pretty=oneline ${commitRange}".execute().text
        output.debugln("git diff --name-only --pretty=oneline ${commitRange}\nreturned these changes:\n%s", committedChanges)
      } else {
        // attempt `gh pr diff`. Will fail if not a PR or gh cli not installed
        Process prChanges = "gh pr diff --name-only".execute()
        boolean notTimedOut = prChanges.waitFor(10, TimeUnit.SECONDS)
        if (notTimedOut && prChanges.exitValue() == 0) {
          committedChanges = prChanges.text
          output.debugln("gh pr diff returned these changes:\n%s", committedChanges)
        } else {
          // fall back to branch and origin diff
          output.debugln("gh pr diff command not successful. Falling back to git diff")

          committedChanges = "git diff --name-only --pretty=oneline --no-merges origin..HEAD".execute().text
          output.debugln("git diff --name-only --pretty=oneline --no-merges origin..HEAD\nreturned these changes:\n%s", committedChanges)
        }
      }
      String uncommittedChanges = "git diff -u --name-only HEAD".execute().text
      output.debugln("uncommitted changes:\n%s", committedChanges)

      processGitOutput(committedChanges, classNames)
      processGitOutput(uncommittedChanges, classNames)

      List<String> dashTestFlags = new ArrayList<>()
      for (String s : classNames) {
        dashTestFlags.add("--test=" + s)
      }
      output.infoln("Found [" + classNames.size() + "] tests to run from changed files. Equivalent to running:\nsb test " + String.join(" ", dashTestFlags))
    } else {
      // Normal test execution, collect all tests
      project.publications.group("test").each { publication ->
        JarFile jarFile = new JarFile(publication.file.toFile())
        jarFile.entries().each { entry ->
          if (!entry.directory && includeEntry(entry)) {
            classNames.add(entry.name.replace("/", ".").replace(".class", ""))
          }
        }
      }

      if (runtimeConfiguration.switches.valueSwitches.containsKey("test")) {
        output.infoln("Running [${classNames.size()}] tests requested by the test switch matching [" + runtimeConfiguration.switches.valueSwitches.get("test").join(",") + "]")
      } else {
        output.infoln("Running all tests. Found [${classNames.size()}] tests.")
      }
    }

    Path xmlFile = FileTools.createTempPath("savant", "testng.xml", true)
    BufferedWriter writer = Files.newBufferedWriter(xmlFile, Charset.forName("UTF-8"))
    MarkupBuilder xml = new MarkupBuilder(writer)
    xml.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
    xml.mkp.yieldUnescaped('<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">\n')
    xml.suite(name: "All Tests", "allow-return-values": "true", verbose: "${settings.verbosity}") {
      delegate.test(name: "All Tests") {
        if ((groups != null && groups.size() > 0) || (excludes != null && excludes.size() > 0)) {
          delegate.groups {
            delegate.run {
              if (groups != null) {
                groups.each { group -> delegate.include(name: group) }
              }
              if (excludes != null) {
                excludes.each { group -> delegate.exclude(name: group) }
              }
            }
          }
        }

        delegate.classes {
          classNames.each { className -> delegate."class"(name: className) }
        }
      }

      if (!settings.listeners.empty) {
        xml.listeners {
          settings.listeners.each { listenerClass ->
            listener("class-name": listenerClass)
          }
        }
      }
    }

    writer.flush()
    writer.close()
    output.debugln("TestNG XML file contents are:\n${new String(Files.readAllBytes(xmlFile), "UTF-8")}")
    return xmlFile
  }

  boolean includeEntry(JarEntry entry) {
    String name = entry.name
    if (!name.endsWith("Test.class")) {
      return false
    }

    String modifiedName = name.substring(0, name.length() - 6)
    String simpleName = modifiedName.substring(name.lastIndexOf("/") + 1)
    String fqName = modifiedName.replace("/", ".")

    if (runtimeConfiguration.switches.valueSwitches.containsKey("test")) {
      List<String> requestedTests = runtimeConfiguration.switches.valueSwitches.get("test")
      // If we have an exact match, keep it.
      for (String test : requestedTests) {
        if (test == simpleName || test == fqName) {
          return true
        }
      }

      // Else do a fuzzy match, match all tests with the name in it
      for (String test : requestedTests) {
        if (name.contains(test)) {
          return true;
        }
      }

      return false;
    }

    return true
  }

  private void initialize() {
    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  javaTestNG.settings.javaVersion=\"1.7\"")
    }

    String javaHome = properties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n${ERROR_MESSAGE}")
    }

    javaPath = Paths.get(javaHome, "bin/java")
    if (!Files.isRegularFile(javaPath)) {
      fail("The java executable [${javaPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(javaPath)) {
      fail("The java executable [${javaPath.toAbsolutePath()}] is not executable.")
    }
  }

  private findFailedTests(File testResults, Set<String> classNames) {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
    Document document = documentBuilder.parse(testResults);

    def testElement = (Element) document.getElementsByTagName("test").item(0);
    NodeList testClasses = testElement.getElementsByTagName("class")
    for (int i = 0; i < testClasses.length; i++) {
      def testClassElement = (Element) testClasses.item(i)
      NodeList testMethods = testClassElement.getElementsByTagName("test-method")
      for (int j = 0; j < testMethods.length; j++) {
        def testMethodElement = (Element) testMethods.item(j)
        if ("FAIL" == testMethodElement.getAttribute("status")) {
          // Currently if any methods fail in a class, we are going to re-run the entire test class.
          classNames.add(testClassElement.getAttribute("name"))
          break;
        }
      }
    }
  }

  private Path getLastTestResultsPath() {
    String tmpDir = System.getProperty("java.io.tmpdir")
    return Paths.get(tmpDir).resolve(project.name + "/test-reports/last/testng-results.xml")
  }

  /**
   * Process terse git output into a list of java test classes
   * @param changes output from git
   * @param testClasses set of classes we'll add to
   */
  private static void processGitOutput(String changes, Set<String> testClasses) {
    Pattern testFilePattern = Pattern.compile("src/test/java/(.*Test).java")
    Pattern mainFilePattern = Pattern.compile("src/main/java/(.*).java")

    for (String change : changes.lines()) {
      Matcher testMatcher = testFilePattern.matcher(change)
      if (testMatcher.matches()) {
        // it's a java test file
        String testFile = testMatcher.group(0)

        // ensure it still exists
        if (Files.exists(Paths.get(testFile))) {
          String classPathFile = testMatcher.group(1)
          String testClass = classPathFile.replaceAll("/", ".")
          testClasses.add(testClass)
        }
      } else {
        Matcher mainMatcher = mainFilePattern.matcher(change)
        if (mainMatcher.matches()) {
          // it's a java file, look for a corresponding test
          String file = mainMatcher.group(1)
          String testClass = file.replaceAll("/", ".") + "Test"
          if (!testClasses.contains(testClass)) {
            String testFile = "src/test/java/" + file + "Test.java"
            if (Files.exists(Paths.get(testFile))) {
              testClasses.add(testClass)
            }
          }
        }
      }
    }
  }

  def produceCodeCoverageReports() {
    def loader = new ExecFileLoader()
    // produced by the Java Agent we instrumented our test run with
    def execFile = getCodeCoverageFile()
    if (!execFile.exists()) {
      fail("${execFile} was not found")
    }
    loader.load(execFile)
    def builder = new CoverageBuilder()

    def analyzer = new Analyzer(loader.executionDataStore, builder)
    // refine our analysis to only include the main JAR that we publish
    def coverageClassPath = dependencyPlugin.classpath {
      project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
    }
    // add each JAR from the Savant main publication group to be analyzed
    coverageClassPath.paths.each { p ->
      analyzer.analyzeAll(p.toFile())
    }

    def bundle = builder.getBundle("JaCoCo Coverage Report - ${project.name}")
    def formatter = new HTMLFormatter()
    def reportDirectory = new File(project.directory.toFile(), "build/coverage-reports")
    def visitor = formatter.createVisitor(new FileMultiReportOutput(reportDirectory))
    visitor.visitInfo(loader.sessionInfoStore.infos,
        loader.executionDataStore.contents)
    def sourceLocator = new MultiSourceFileLocator(4)
    sourceLocator.add(new DirectorySourceFileLocator(new File(project.directory.toFile(), "src/main/java"),
        "utf-8",
        4))
    visitor.visitBundle(bundle, sourceLocator)
    visitor.visitEnd()
  }

  File getCodeCoverageFile() {
    project.directory.resolve("build/jacoco.exec").toAbsolutePath().toFile()
  }

  String getCodeCoverageArguments() {
    if (!settings.codeCoverage) {
      return ""
    }
    def jacocoPath = AgentJar.extractToTempLocation()
    " -javaagent:${jacocoPath}=destfile=${getCodeCoverageFile()}"
  }
}
