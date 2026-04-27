/*
 * Copyright (c) 2013-2024, Inversoft Inc., All Rights Reserved
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

import com.tonicsystems.jarjar.Main
import org.lattejava.cli.domain.Project
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.dep.domain.ArtifactID
import org.lattejava.io.FileSet
import org.lattejava.io.FileTools
import org.lattejava.lang.Classpath
import org.lattejava.output.Output
import org.lattejava.plugin.dep.DependencyPlugin
import org.lattejava.plugin.file.FilePlugin

import java.lang.module.ModuleFinder
import java.lang.module.ModuleReference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import java.util.function.Predicate
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * The Java plugin. The public methods on this class define the features of the plugin.
 */
class JavaPlugin extends BaseGroovyPlugin {
  public static final String ERROR_MESSAGE = """You must create the file [~/.config/latte/plugins/org.lattejava.plugin.java.properties] that contains the system configuration for the Java system. This file should include the location of the JDK (java and javac) by version. These properties look like this:

  21=%USER_HOME%/.local/share/java/21.0.10+7
  25=%USER_HOME%/.local/share/java/25.0.2+10
""".replace("%USER_HOME%", System.getProperty("user.home"))

  JavaLayout layout = new JavaLayout()

  JavaSettings settings = new JavaSettings()

  Properties properties

  Path javaDocPath

  Path javaPath

  Path javacPath

  FilePlugin filePlugin

  DependencyPlugin dependencyPlugin

  JavaPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    filePlugin = new FilePlugin(project, runtimeConfiguration, output)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
    properties = loadConfiguration(new ArtifactID("org.lattejava.plugin", "java", "java", "jar"), ERROR_MESSAGE)
  }

  /**
   * Lazily initializes module-build settings from the on-disk module-info.java files. Runs on first
   * plugin method invocation rather than in the constructor, so {@code project.latte} overrides of
   * {@link JavaSettings#moduleBuild}, {@link JavaSettings#testModuleBuild}, and {@link JavaLayout}
   * paths are honored. Idempotent: settings already set to non-null are left alone.
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
   * Cleans the build directory by completely deleting it.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.clean()
   * </pre>
   */
  void clean() {
    init()
    Path buildDir = project.directory.resolve(layout.buildDirectory)
    output.infoln "Cleaning [${buildDir}]"
    FileTools.prune(buildDir)
  }

  /**
   * Compiles the main and test Java files (src/main/java and src/test/java).
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.compile()
   * </pre>
   */
  void compile() {
    init()
    compileMain()
    compileTest()
  }

  /**
   * Compiles the main Java files (src/main/java by default).
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.compileMain()
   * </pre>
   */
  void compileMain() {
    init()
    compileInternal(layout.mainSourceDirectory, layout.mainBuildDirectory, settings.mainDependencies, "", layout.mainBuildDirectory)
    copyResources(layout.mainResourceDirectory, layout.mainBuildDirectory)
  }

  /**
   * Compiles the test Javafiles (src/test/java by default).
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.compileTest()
   * </pre>
   */
  void compileTest() {
    init()
    if (settings.testModuleBuild) {
      if (!settings.moduleBuild) {
        fail("testModuleBuild is enabled but moduleBuild is not. A separate test module requires " +
            "src/main/java/module-info.java to also exist.")
      }

      // Separate test module: all deps (main + test) go on --module-path.
      // The user's test module-info.java must declare `requires` for everything tests use
      // (main module, testng, easymock, etc.). Main build dir is on --module-path so the
      // test module can resolve `requires <mainModule>`. No --patch-module, no --add-reads.
      //
      // testBuildDirectory is intentionally NOT on --module-path: doing so would put the
      // in-progress test module on the path at the same location being compiled, which would
      // conflict with the source being compiled. Incremental recompiles resolve sibling test
      // classes via -sourcepath (set inside compileInternal) rather than precompiled classes.
      compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
          settings.testDependencies, "", layout.mainBuildDirectory)
    } else if (settings.moduleBuild) {
      String moduleName = resolveModuleName()
      // Test-only deps (e.g. TestNG) go on -classpath so they land in the unnamed module,
      // accessible via --add-reads. Main deps + main build dir go on --module-path.
      String testClasspath = dependencyPlugin.classpath {
        settings.testDependencies.findAll { it.group != "compile" && it.group != "provided" }
            .each { deps -> dependencies(deps) }
      }.toString("-classpath ")
      String moduleArgs = "${testClasspath} --patch-module ${moduleName}=${layout.testSourceDirectory} --add-reads ${moduleName}=ALL-UNNAMED"
      compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
          settings.mainDependencies, moduleArgs, layout.mainBuildDirectory, layout.testBuildDirectory)
    } else {
      compileInternal(layout.testSourceDirectory, layout.testBuildDirectory,
          settings.testDependencies, "", layout.mainBuildDirectory, layout.testBuildDirectory)
    }
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }

  /**
   * Creates the project's JavaDoc. This executes the javadoc command and outputs the docs to the {@code layout.docDirectory}
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.document()
   * </pre>
   */
  void document() {
    init()
    initialize()

    output.infoln("Generating JavaDoc to [%s]", layout.docDirectory)

    FileSet fileSet = new FileSet(project.directory.resolve(layout.mainSourceDirectory))
    Set<String> packages = fileSet.toFileInfos()
        .stream()
        .map({ info -> info.relative.getParent().toString().replace("/", ".") })
        .collect(Collectors.toSet())

    String moduleArgs = settings.moduleBuild ? "--module ${resolveModuleName()}" : ""
    String command = "${javaDocPath} ${pathString(settings.mainDependencies, settings.libraryDirectories)} ${settings.docArguments} -sourcepath ${layout.mainSourceDirectory} -d ${layout.docDirectory} ${moduleArgs} ${packages.join(" ")}"
    output.debugln("Executing JavaDoc command [%s]", command)

    Process process = command.execute([], project.directory.toFile())
    process.consumeProcessOutput((Appendable) System.out, System.err)
    process.waitFor()

    int exitCode = process.exitValue()
    if (exitCode != 0) {
      fail("JavaDoc failed")
    }
  }

  /**
   * Creates the project's Jar files. This creates four Jar files. The main Jar, main source Jar, test Jar and test
   * source Jar.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.jar()
   * </pre>
   */
  void jar() {
    init()
    initialize()

    jarInternal(project.toArtifact().getArtifactFile(), layout.mainBuildDirectory)
    jarInternal(project.toArtifact().getArtifactSourceFile(), layout.mainSourceDirectory, layout.mainResourceDirectory)
    jarInternal(project.toArtifact().getArtifactTestFile(), layout.testBuildDirectory)
    jarInternal(project.toArtifact().getArtifactTestSourceFile(), layout.testSourceDirectory, layout.testResourceDirectory)
  }

  void jarjar(Map<String, Object> attributes, @DelegatesTo(JarJarDelegate.class) Closure closure) {
    init()
    if (!GroovyTools.attributesValid(attributes, ["dependencyGroup", "operation", "outputDirectory"],
        ["dependencyGroup"],
        ["dependencyGroup": String.class, "operation": String.class, "outputDirectory": String.class])) {
      fail("You must supply the name of the dependency group like this:\n\n" +
          "  java.jarjar(dependencyGroup: \"nasty-deps\", outputDirectory: \"build/classes/main\")")
    }

    GroovyTools.putDefaults(attributes, ["outputDirectory": "build/classes/main"])

    String dependencyGroup = attributes["dependencyGroup"].toString()
    Path outputDirectory = FileTools.toPath(attributes["outputDirectory"])

    JarJarDelegate delegate = new JarJarDelegate()
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()

    Path rulesFile = delegate.buildRulesFile()
    if (rulesFile == null) {
      fail("You must supply rules as nested elements of the JarJar method like this::\n\n" +
          "  java.jarjar(dependencyGroup: \"nasty-deps\", outputDirectory: \"build/classes/main\") {\n" +
          "    rule(from: \"foo\", to: \"bar\"\n" +
          "   }\n")
    }

    // Step 1 - copy all the dependencies in the group to a temp dir
    Path depsTempDir = Files.createTempDirectory("jarjar-deps")
    dependencyPlugin.copy(to: depsTempDir) {
      dependencies(group: dependencyGroup, transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"])
    }

    // Step 2 - explode all the JARs into another temp directory
    Path explosionTempDir = Files.createTempDirectory("jarjar-explosion")
    Files.list(depsTempDir).forEach { file ->
      if (Files.isRegularFile(file) && file.toString().endsWith(".jar")) {
        filePlugin.unjar(file: file, to: explosionTempDir)
      }
    }

    // Step 3 - remove the META-INF files that mess things up
    filePlugin.delete {
      fileSet(dir: explosionTempDir.resolve("META-INF"), includePatterns: [~/MANIFEST.MF/, ~/.*DSA/, ~/.*SF/])
    }

    // Step 4 - build an intermediate JAR that contains everything from the dependency JARs in their original packages
    Path intermediateTempDir = Files.createTempDirectory("jarjar-intermediate")
    Path intermediateJar = intermediateTempDir.resolve("intermediate.jar")
    filePlugin.mkdir(dir: intermediateTempDir)
    filePlugin.jar(file: intermediateJar) {
      fileSet(dir: explosionTempDir)
    }

    // Step 5 - run JarJar to repackage things (shade)
    output.infoln("Running JarJar")
    Path processedJar = intermediateTempDir.resolve("processed.jar")
    Main.main("process", rulesFile.toAbsolutePath().toString(), intermediateJar.toString(), processedJar.toString())
    output.infoln("JarJar completed successfully")

    // Step 6 - explode the JarJar output JAR into the classes directory used by Latte. This will be added to the JAR file for the project
    filePlugin.mkdir(dir: outputDirectory)
    filePlugin.unjar(file: processedJar.toString(), to: outputDirectory)

    // Step 7 - once again, remove the META-INF files that we don't need but this time from the classes directory since this is where we build the project JAR from
    filePlugin.delete {
      fileSet(dir: outputDirectory.resolve("META-INF"), includePatterns: [~/MANIFEST.MF/, ~/.*DSA/, ~/.*SF/])
    }

    // Clean up
    FileTools.prune(depsTempDir)
    FileTools.prune(intermediateTempDir)
    Files.deleteIfExists(rulesFile)
  }

  /**
   * Returns the main classpath for the project
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.getMainClasspath()
   * </pre>
   */
  String getMainClasspath() {
    init()
    return pathString([
        [group: "compile", transitive: true, fetchSource: false, transitiveGroups: ["compile", "runtime", "provided"]],
        [group: "runtime", transitive: true, fetchSource: false, transitiveGroups: ["compile", "runtime", "provided"]],
        [group: "provided", transitive: true, fetchSource: false, transitiveGroups: ["compile", "runtime", "provided"]],
    ], settings.libraryDirectories, layout.mainBuildDirectory)
  }

  void printJDKModuleDeps() {
    init()
    def jdeps = "${properties.get(settings.javaVersion)}/bin/jdeps"

    output.debugln("Running [${jdeps} --print-module-deps --recursive --ignore-missing-deps --multi-release ${settings.javaVersion} ${getMainClasspath()} ${project.directory}/build/jars/${project.name}-${project.version}.jar]")

    def proc = "${jdeps} --print-module-deps --recursive --ignore-missing-deps --multi-release ${settings.javaVersion} ${getMainClasspath()} ${project.directory}/build/jars/${project.name}-${project.version}.jar".execute()
    proc.consumeProcessOutput(System.out, System.err)
    proc.waitFor()
  }

  /**
   * Runs a Java main class or a {@code .java} source file using the project's current
   * module-path/classpath. Returns the child process exit code; fails the build on a non-zero
   * exit when {@code failOnError} is {@code true} (the default).
   *
   * @param attributes Named attributes. See the design doc for the full list.
   * @return The child process exit code.
   */
  int run(Map<String, Object> attributes) {
    init()

    if (!GroovyTools.attributesValid(attributes,
        ["additionalClasspath", "arguments", "dependencies", "environment", "failOnError", "jvmArguments", "main", "workingDirectory"],
        ["main"],
        ["additionalClasspath": List.class,
         "arguments"          : String.class,
         "dependencies"       : List.class,
         "environment"        : Map.class,
         "failOnError"        : Boolean.class,
         "jvmArguments"       : String.class,
         "main"               : String.class,
         "workingDirectory"   : Object.class])) {
      fail("You must supply the [main] attribute to java.run(), e.g.:\n\n" +
          "  java.run(main: \"com.example.App\")")
    }

    GroovyTools.putDefaults(attributes, [
        "additionalClasspath": [] as List<Path>,
        "arguments"          : "",
        "dependencies"       : defaultRunDependencies(),
        "environment"        : [:] as Map<String, String>,
        "failOnError"        : true,
        "jvmArguments"       : "",
        "workingDirectory"   : project.directory
    ])

    initialize()

    String main = attributes["main"].toString()
    String jvmArguments = attributes["jvmArguments"].toString()
    String arguments = attributes["arguments"].toString()
    List<Map<String, Object>> dependencies = (List<Map<String, Object>>) attributes["dependencies"]
    List<Path> additionalClasspath = ((List<Object>) attributes["additionalClasspath"])
        .collect { FileTools.toPath(it) }
    Path workingDirectory = FileTools.toPath(attributes["workingDirectory"])
    Map<String, String> environment = (Map<String, String>) attributes["environment"]
    boolean failOnError = (Boolean) attributes["failOnError"]

    // Build the additional-paths list: caller extras + the project's main publications.
    List<Path> publicationPaths = project.publications.group("main")
        .collect { it.file.toAbsolutePath() }
    List<Path> allAdditionalPaths = new ArrayList<>(additionalClasspath)
    allAdditionalPaths.addAll(publicationPaths)

    String pathArgs
    String entryPoint

    if (main.endsWith(".java")) {
      Path resolvedSource = workingDirectory.resolve(main)
      if (!Files.isRegularFile(resolvedSource) || !Files.isReadable(resolvedSource)) {
        fail("Main source file [%s] does not exist or is not readable", resolvedSource.toAbsolutePath())
      }

      Classpath resolved = resolveRunClasspath(dependencies, settings.libraryDirectories, allAdditionalPaths)
      if (settings.moduleBuild) {
        // Source-file runs have no module-info, so the source compiles into the unnamed
        // module. Modules on --module-path aren't auto-resolved without --add-modules,
        // so collect every module name on the path and add it explicitly.
        pathArgs = resolved.toString("--module-path ")
        Set<String> modules = collectModuleNames(resolved.paths)
        if (!modules.isEmpty()) {
          jvmArguments = "${jvmArguments} --add-modules ${modules.join(',')}".trim()
        }
      } else {
        pathArgs = resolved.toString("-classpath ")
      }

      entryPoint = resolvedSource.toAbsolutePath().toString()
    } else {
      Classpath resolved = resolveRunClasspath(dependencies, settings.libraryDirectories, allAdditionalPaths)
      RunEntryMatch match = findMainClassEntry(resolved.paths, main)
      if (match == null) {
        fail("Main class [%s] was not found on the resolved classpath/module-path", main)
      }

      if (match.moduleName != null) {
        entryPoint = "--module ${match.moduleName}/${main}"
        pathArgs = pathString(dependencies, settings.libraryDirectories, allAdditionalPaths.toArray(new Path[0]))
      } else if (settings.moduleBuild) {
        // Non-modular entry inside a module build: split paths so the match lands on -classpath.
        Classpath modulePath = new Classpath()
        Classpath classpath = new Classpath()
        resolved.paths.each { p ->
          if (p == match.entry) {
            classpath.path(p)
          } else {
            modulePath.path(p)
          }
        }
        pathArgs = "${modulePath.toString("--module-path ")} ${classpath.toString("-classpath ")}"
        entryPoint = main
      } else {
        entryPoint = main
        pathArgs = pathString(dependencies, settings.libraryDirectories, allAdditionalPaths.toArray(new Path[0]))
      }
    }

    return executeRun(jvmArguments, pathArgs, entryPoint, arguments, workingDirectory, environment, failOnError)
  }

  /**
   * Compiles an arbitrary source directory to an arbitrary build directory.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.compile(Paths.get("src/foo"), Paths.get("build/bar"), [[group: "compile", transitive: false, fetchSource: false]], Paths.get("additionalClasspathDirectory"))
   * </pre>
   *
   * @param sourceDirectory The source directory that contains the Java source files.
   * @param buildDirectory The build directory to compile the Java files to.
   * @param dependencies The dependencies to resolve and include on the compile classpath.
   */
  private void compileInternal(Path sourceDirectory, Path buildDirectory, List<Map<String, Object>> dependencies, String extraArgs, Path... additionalClasspath) {
    initialize()

    Path resolvedSourceDir = project.directory.resolve(sourceDirectory)
    Path resolvedBuildDir = project.directory.resolve(buildDirectory)

    output.debugln("Looking for modified files to compile in [%s] compared with [%s]", resolvedSourceDir, resolvedBuildDir)

    Predicate<Path> filter = FileTools.extensionFilter(".java")
    Function<Path, Path> mapper = FileTools.extensionMapper(".java", ".class")
    List<Path> filesToCompile = FileTools.modifiedFiles(resolvedSourceDir, resolvedBuildDir, filter, mapper)
        .collect({ path -> sourceDirectory.resolve(path) })
    if (filesToCompile.isEmpty()) {
      output.infoln("Skipping compile for source directory [%s]. No files need compiling", sourceDirectory)
      return
    }

    output.infoln("Compiling [${filesToCompile.size()}] Java classes from [${sourceDirectory}] to [${buildDirectory}]")

    String command = "${javacPath} ${settings.compilerArguments} ${pathString(dependencies, settings.libraryDirectories, additionalClasspath)} ${extraArgs} -sourcepath ${sourceDirectory} -d ${buildDirectory} ${filesToCompile.join(" ")}"
    output.debugln("Executing compiler command [%s]", command)

    Files.createDirectories(resolvedBuildDir)
    Process process = command.execute(null, project.directory.toFile())
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
   *   java.copyResources(Paths.get("src/some-resources"), Paths.get("build/output-dir"))
   * </pre>
   *
   * @param sourceDirectory The source directory that contains the files to copy.
   * @param buildDirectory The build directory to copy the files to.
   */
  private void copyResources(Path sourceDirectory, Path buildDirectory) {
    if (!Files.isDirectory(project.directory.resolve(sourceDirectory))) {
      return
    }

    filePlugin.copy(to: buildDirectory) {
      fileSet(dir: sourceDirectory)
    }
  }

  private static List<Map<String, Object>> defaultRunDependencies() {
    return [
        [group           : "compile", transitive: true, fetchSource: false,
         transitiveGroups: ["compile", "runtime", "provided"]],
        [group           : "runtime", transitive: true, fetchSource: false,
         transitiveGroups: ["compile", "runtime", "provided"]],
        [group           : "provided", transitive: true, fetchSource: false,
         transitiveGroups: ["compile", "runtime", "provided"]]
    ]
  }

  private int executeRun(String jvmArguments, String pathArgs, String entryPoint,
                         String arguments, Path workingDirectory,
                         Map<String, String> environment, boolean failOnError) {
    String command = "${javaPath} ${jvmArguments} ${pathArgs} ${entryPoint} ${arguments}"
    output.debugln("Executing java command [%s]", command)

    List<String> args = new StringTokenizer(command).toList() as List<String>

    ProcessBuilder pb = new ProcessBuilder(args)
        .inheritIO()
        .directory(workingDirectory.toFile())
    pb.environment().putAll(environment)

    Process process = pb.start()
    int exitCode = process.waitFor()

    if (exitCode != 0 && failOnError) {
      fail("java command failed with exit code [%d]", exitCode)
    }

    return exitCode
  }

  /**
   * Creates a single Jar file by adding all of the files in the given directories.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   java.jar(Paths.get("foo/bar.jar"), Paths.get("src/main/groovy"), Paths.get("some-other-dir"))
   * </pre>
   *
   * @param jarFile The Jar file to create.
   * @param directories The directories to include in the Jar file.
   */
  private void jarInternal(String jarFile, Path... directories) {
    Path jarFilePath = layout.jarOutputDirectory.resolve(jarFile)

    output.infoln("Creating JAR [%s]", jarFile)

    filePlugin.jar(file: jarFilePath) {
      directories.each { dir ->
        optionalFileSet(dir: dir)
      }
    }
  }

  static RunEntryMatch findMainClassEntry(List<Path> entries, String main) {
    String classPath = main.replace('.', '/') + ".class"

    for (Path entry : entries) {
      if (Files.isDirectory(entry)) {
        Path classFile = entry.resolve(classPath)
        if (!Files.isRegularFile(classFile)) {
          continue
        }

        RunEntryMatch m = new RunEntryMatch(entry: entry)
        m.moduleName = readExplicitModuleName(entry)
        return m
      }

      if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
        try (JarFile jarFile = new JarFile(entry.toFile())) {
          if (jarFile.getJarEntry(classPath) == null) {
            continue
          }

          RunEntryMatch m = new RunEntryMatch(entry: entry)

          // Prefer an explicit module-info.class. Fall back to Automatic-Module-Name in the
          // manifest. Filename-derived automatic module names are intentionally not used here.
          String name = readExplicitModuleName(entry)
          if (name != null) {
            m.moduleName = name
            return m
          }

          Manifest manifest = jarFile.getManifest()
          if (manifest != null) {
            String autoName = manifest.getMainAttributes().getValue("Automatic-Module-Name")
            if (autoName != null && !autoName.isEmpty()) {
              m.moduleName = autoName
            }
          }
          return m
        }
      }
    }

    return null
  }

  private static Set<String> collectModuleNames(List<Path> paths) {
    if (paths == null || paths.isEmpty()) {
      return Collections.emptySet()
    }

    ModuleFinder finder = ModuleFinder.of(paths.toArray(new Path[0]))
    Set<String> names = new TreeSet<>()
    finder.findAll().each { ref -> names.add(ref.descriptor().name()) }
    return names
  }

  private static String readExplicitModuleName(Path entry) {
    ModuleFinder finder = ModuleFinder.of(entry)
    for (ModuleReference ref : finder.findAll()) {
      if (!ref.descriptor().isAutomatic()) {
        return ref.descriptor().name()
      }
    }
    return null
  }

  private String resolveModuleName() {
    Path buildDir = project.directory.resolve(layout.mainBuildDirectory)
    Path moduleInfoClass = buildDir.resolve("module-info.class")
    if (!Files.isRegularFile(moduleInfoClass)) {
      fail("Module build is enabled but module-info.class was not found in [%s]. Ensure main sources are compiled first.", layout.mainBuildDirectory)
    }

    String name = readExplicitModuleName(buildDir)
    if (!name) {
      fail("Failed to extract module name from [%s]", moduleInfoClass)
    }

    return name
  }

  private Classpath resolveRunClasspath(List<Map<String, Object>> dependenciesList,
                                        List<Path> libraryDirectories,
                                        List<Path> additionalPaths) {
    List<Path> additionalJARs = new ArrayList<>()
    if (libraryDirectories != null) {
      libraryDirectories.each { path ->
        Path dir = project.directory.resolve(FileTools.toPath(path))
        if (!Files.isDirectory(dir)) {
          return
        }
        try (Stream<Path> stream = Files.list(dir)) {
          stream.filter(FileTools.extensionFilter(".jar"))
              .forEach { file -> additionalJARs.add(file.toAbsolutePath()) }
        }
      }
    }

    return dependencyPlugin.classpath {
      dependenciesList.each { deps -> dependencies(deps) }
      additionalPaths.each { p -> path(location: p) }
      additionalJARs.each { jar -> path(location: jar) }
    }
  }

  private String pathString(List<Map<String, Object>> dependenciesList, List<Path> libraryDirectories, Path... additionalPaths) {
    List<Path> additionalJARs = new ArrayList<>()
    if (libraryDirectories != null) {
      libraryDirectories.each { path ->
        Path dir = project.directory.resolve(FileTools.toPath(path))
        if (!Files.isDirectory(dir)) {
          return
        }

        try (Stream<Path> stream = Files.list(dir)) {
          stream.filter(FileTools.extensionFilter(".jar")).forEach { file -> additionalJARs.add(file.toAbsolutePath()) }
        }
      }
    }

    String prefix = settings.moduleBuild ? "--module-path " : "-classpath ";
    return dependencyPlugin.classpath {
      dependenciesList.each { deps -> dependencies(deps) }
      additionalPaths.each { additionalPath -> path(location: additionalPath) }
      additionalJARs.each { additionalJAR -> path(location: additionalJAR) }
    }.toString(prefix)
  }

  String getJavaHome() {
    properties.getProperty(settings.javaVersion)
  }

  private void initialize() {
    if (javacPath) {
      return
    }

    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  java.settings.javaVersion=\"1.7\"")
    }

    String javaHome = getJavaHome()
    if (!javaHome) {
      fail("No JDK is configured for version [%s].\n\n[%s]", settings.javaVersion, ERROR_MESSAGE)
    }

    javacPath = Paths.get(javaHome, "bin/javac")
    if (!Files.isRegularFile(javacPath)) {
      fail("The javac compiler [%s] does not exist.", javacPath.toAbsolutePath())
    }
    if (!Files.isExecutable(javacPath)) {
      fail("The javac compiler [%s] is not executable.", javacPath.toAbsolutePath())
    }

    javaDocPath = Paths.get(javaHome, "bin/javadoc")
    if (!Files.isRegularFile(javaDocPath)) {
      fail("The javac compiler [%s] does not exist.", javaDocPath.toAbsolutePath())
    }
    if (!Files.isExecutable(javaDocPath)) {
      fail("The javac compiler [%s] is not executable.", javaDocPath.toAbsolutePath())
    }

    javaPath = Paths.get(javaHome, "bin/java")
    if (!Files.isRegularFile(javaPath)) {
      fail("The java executable [%s] does not exist.", javaPath.toAbsolutePath())
    }
    if (!Files.isExecutable(javaPath)) {
      fail("The java executable [%s] is not executable.", javaPath.toAbsolutePath())
    }
  }

  static class RunEntryMatch {
    Path entry
    String moduleName  // null if non-modular
  }
}
