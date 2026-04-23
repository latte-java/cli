/*
 * Copyright (c) 2013-2026, Inversoft Inc., All Rights Reserved
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

import groovy.xml.MarkupBuilder
import org.jacoco.agent.AgentJar
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.tools.ExecFileLoader
import org.jacoco.report.DirectorySourceFileLocator
import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.MultiSourceFileLocator
import org.jacoco.report.html.HTMLFormatter
import org.lattejava.cli.domain.Project
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.dep.domain.ArtifactID
import org.lattejava.io.FileTools
import org.lattejava.lang.Classpath
import org.lattejava.output.Output
import org.lattejava.plugin.dep.DependencyPlugin
import org.lattejava.plugin.file.FilePlugin
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ModuleVisitor
import org.objectweb.asm.Opcodes

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

/**
 * The Java TestNG plugin. The public methods on this class define the features of the plugin.
 */
class JavaTestNGPlugin extends BaseGroovyPlugin {
  public static final String ERROR_MESSAGE = """You must create the file [~/.config/latte/plugins/org.lattejava.plugin.java.properties] that contains the system configuration for the Java system. This file should include the location of the JDK (java and javac) by version. These properties look like this:

  21=%USER_HOME%/.local/share/java/21.0.10+7
  25=%USER_HOME%/.local/share/java/25.0.2+10
""".replace("%USER_HOME%", System.getProperty("user.home"))

  DependencyPlugin dependencyPlugin

  FilePlugin filePlugin

  Path javaPath

  Properties properties

  JavaTestNGSettings settings = new JavaTestNGSettings()

  JavaLayout layout = new JavaLayout()

  JavaTestNGPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    properties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "java", "java", "jar"), ERROR_MESSAGE)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
    filePlugin = new FilePlugin(project, runtimeConfiguration, output)
  }

  /**
   * Lazily initializes module-build settings from the on-disk module-info.java files. Runs on first
   * plugin method invocation rather than in the constructor, so {@code project.latte} overrides of
   * {@link JavaTestNGSettings#moduleBuild}, {@link JavaTestNGSettings#testModuleBuild}, and
   * {@link JavaLayout} paths are honored. Idempotent.
   */
  private void init() {
    if (settings.moduleBuild == null) {
      settings.moduleBuild = Files.isRegularFile(project.directory.resolve(layout.mainSourceDirectory).resolve("module-info.java"))
    }
    if (settings.testModuleBuild == null) {
      settings.testModuleBuild = Files.isRegularFile(project.directory.resolve(layout.testSourceDirectory).resolve("module-info.java"))
    }
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
    init()

    if (runtimeConfiguration.switches.booleanSwitches.contains("skipTests")) {
      output.infoln("Skipping tests")
      return
    }

    initialize()

    // Initialize the attributes if they are null
    if (!attributes) {
      attributes = [:]
    }

    // --onlyFailed hands off the preserved testng-failed.xml directly to TestNG.
    // No XML generation; any groups/exclude attributes are ignored.
    Path failedXml = null
    if (runtimeConfiguration.switches.booleanSwitches.contains("onlyFailed")) {
      Path preserved = lastFailedTestsPath()
      if (!Files.isRegularFile(preserved)) {
        output.infoln("No failed tests found from a prior test run. File not found [" + preserved.toString() + "].")
        return
      }
      output.infoln("Re-running failed tests from [" + preserved.toString() + "].")
      failedXml = preserved
    }

    String classpathArgs
    String testngEntry
    if (settings.testModuleBuild) {
      if (!settings.moduleBuild) {
        fail("testModuleBuild is enabled but moduleBuild is not. A separate test module requires " +
            "src/main/java/module-info.java to also exist.")
      }

      String testModuleName = resolveTestModuleName()

      // Everything on --module-path: main deps, test deps, main publication(s), test publication(s).
      // The test module's module-info.java declares its own requires for main module, testng, etc.
      Classpath modulePath = dependencyPlugin.classpath {
        settings.dependencies.each { deps -> dependencies(deps) }
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
        project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }

      // --add-modules ALL-MODULE-PATH resolves all JARs on the module path (including automatic modules
      // like slf4j that TestNG depends on). The test module is also explicitly named so its @Test
      // classes are discoverable.
      // No --add-opens: the user's test module-info.java must declare `opens <pkg> to org.testng;`
      // for every package containing test classes.
      classpathArgs = "${modulePath.toString("--module-path ")} --add-modules ALL-MODULE-PATH,${testModuleName}"

      // TestNG 7+ ships Automatic-Module-Name: org.testng, so it resolves as an automatic module.
      testngEntry = "--module org.testng/org.testng.TestNG"
    } else if (settings.moduleBuild) {
      String moduleName = resolveModuleName()

      // Main deps + main publications go on --module-path
      Classpath modulePath = dependencyPlugin.classpath {
        settings.dependencies.findAll { it.group != "test-compile" && it.group != "test-runtime" }
            .each { deps -> dependencies(deps) }
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }

      // Test-only deps go on -classpath (unnamed module)
      Classpath testClasspath = dependencyPlugin.classpath {
        settings.dependencies.findAll { it.group == "test-compile" || it.group == "test-runtime" }
            .each { deps -> dependencies(deps) }
      }

      // Patch test publications into the module
      String testPubPaths = project.publications.group("test")
          .collect { it.file.toAbsolutePath().toString() }
          .join(File.pathSeparator)

      // Extract packages from test JARs for --add-opens so TestNG can reflectively access test classes
      Set<String> packages = new TreeSet<>()
      project.publications.group("test").each { publication ->
        try (JarFile jarFile = new JarFile(publication.file.toFile())) {
          jarFile.entries().each { entry ->
            if (!entry.directory && entry.name.endsWith(".class")) {
              int lastSlash = entry.name.lastIndexOf("/")
              if (lastSlash > 0) {
                packages.add(entry.name.substring(0, lastSlash).replace("/", "."))
              }
            }
          }
        }
      }
      String addOpens = packages.collect { "--add-opens ${moduleName}/${it}=ALL-UNNAMED" }.join(" ")

      classpathArgs = "${modulePath.toString("--module-path ")} ${testClasspath.toString("-classpath ")} --add-modules ${moduleName} --patch-module ${moduleName}=${testPubPaths} --add-reads ${moduleName}=ALL-UNNAMED ${addOpens}"
      testngEntry = "org.testng.TestNG"
    } else {
      Classpath classpath = dependencyPlugin.classpath {
        settings.dependencies.each { deps -> dependencies(deps) }

        // Publications are already resolved by now, therefore, we convert them to absolute paths so they won't be resolved again
        project.publications.group("main").each { publication -> path(location: publication.file.toAbsolutePath()) }
        project.publications.group("test").each { publication -> path(location: publication.file.toAbsolutePath()) }
      }
      classpathArgs = classpath.toString("-classpath ")
      testngEntry = "org.testng.TestNG"
    }

    Path xmlFile = failedXml != null ? failedXml : buildXMLFile(attributes["groups"], attributes["exclude"])
    def jacocoArgs = codeCoverageArguments()
    String command = "${javaPath} ${settings.jvmArguments} ${classpathArgs} ${jacocoArgs} ${testngEntry} -d ${settings.reportDirectory} ${settings.testngArguments} ${xmlFile}"
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

    // Since the XML file is always deleteOnExit, we need to copy it to a safe place
    if (runtimeConfiguration.switches.booleanSwitches.contains("keepXML")) {
      def fileName = "build/test/${xmlFile.fileName.toString()}"
      filePlugin.copyFile(file: xmlFile, to: fileName)
      output.infoln("TestNG configuration saved to [${fileName}]")
    }

    handleExitCode(result)
  }

  /**
   * Reacts to the TestNG process exit code.
   * <ul>
   *   <li>0 - all tests passed; return normally.</li>
   *   <li>2 - tests were skipped but none failed; log and return normally.</li>
   *   <li>1 - tests failed; preserve testng-results.xml and testng-failed.xml, then {@link #fail}.</li>
   *   <li>other - configuration error or unknown; preserve what exists, then {@link #fail}.</li>
   * </ul>
   */
  void handleExitCode(int exitCode) {
    if (exitCode == 0) {
      return
    }

    if (exitCode == 2) {
      output.infoln("TestNG exited with code 2 (tests were skipped).")
      return
    }

    // Preserve both TestNG outputs when there's a failure. The failed file is what
    // --onlyFailed uses on the next run.
    preserveLastRunFile("testng-results.xml", lastTestResultsPath())
    preserveLastRunFile("testng-failed.xml", lastFailedTestsPath())

    if (exitCode == 1) {
      fail("Build failed.")
    } else {
      fail("Build failed (TestNG exit code %d).", exitCode)
    }
  }

  private void preserveLastRunFile(String fileName, Path target) {
    Path source = project.directory.resolve("build/test-reports").resolve(fileName)
    if (!source.toFile().exists()) {
      return
    }

    Files.deleteIfExists(target)
    Files.createDirectories(target.getParent())
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
  }

  Path buildXMLFile(List<String> groups, List<String> excludes) {
    Set<String> classNames = new TreeSet<>()

    if (runtimeConfiguration.switches.booleanSwitches.contains("onlyChanges")) {
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

    Path xmlFile = FileTools.createTempPath("latte", "testng.xml", true)
    BufferedWriter writer = Files.newBufferedWriter(xmlFile, Charset.forName("UTF-8"))
    MarkupBuilder xml = new MarkupBuilder(writer)
    xml.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
    xml.mkp.yieldUnescaped('<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">\n')
    xml.suite(name: "latte-tests", "allow-return-values": "true", verbose: "${settings.verbosity}") {
      delegate.test(name: "all") {
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
          return true
        }
      }

      return false
    }

    return true
  }

  private void initialize() {
    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  javaTestNG.settings.javaVersion=\"25\"")
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

  private String resolveModuleName() {
    String name = findModuleNameInPublications("main")
    if (name == null) {
      fail("Module build is enabled but no module-info.class was found in any main publication JAR.")
    }
    return name
  }

  private String resolveTestModuleName() {
    String name = findModuleNameInPublications("test")
    if (name == null) {
      fail("testModuleBuild is enabled but no module-info.class was found in any test publication JAR. Ensure src/test/java/module-info.java exists and test sources are compiled first.")
    }
    return name
  }

  private String findModuleNameInPublications(String group) {
    for (def pub : project.publications.group(group)) {
      try (JarFile jarFile = new JarFile(pub.file.toFile())) {
        JarEntry moduleInfo = jarFile.getJarEntry("module-info.class")
        if (moduleInfo == null) {
          continue
        }

        byte[] bytes = jarFile.getInputStream(moduleInfo).readAllBytes()
        ClassReader reader = new ClassReader(bytes)
        String[] result = new String[1]
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
          @Override
          ModuleVisitor visitModule(String name, int access, String version) {
            result[0] = name
            return null
          }
        }, 0)

        if (result[0]) {
          return result[0]
        }
      }
    }
    return null
  }

  Path lastTestResultsPath() {
    String tmpDir = System.getProperty("java.io.tmpdir")
    return Paths.get(tmpDir).resolve(project.name + "/test-reports/last/testng-results.xml")
  }

  Path lastFailedTestsPath() {
    String tmpDir = System.getProperty("java.io.tmpdir")
    return Paths.get(tmpDir).resolve(project.name + "/test-reports/last/testng-failed.xml")
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

  private void produceCodeCoverageReports() {
    def loader = new ExecFileLoader()
    // produced by the Java Agent we instrumented our test run with
    def execFile = codeCoverageFile()
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
    // add each JAR from the Latte main publication group to be analyzed
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

  private File codeCoverageFile() {
    return project.directory.resolve("build/jacoco.exec").toAbsolutePath().toFile()
  }

  private String codeCoverageArguments() {
    if (!settings.codeCoverage) {
      return ""
    }
    def jacocoPath = AgentJar.extractToTempLocation()
    return "-javaagent:${jacocoPath}=destfile=${codeCoverageFile()}"
  }
}
