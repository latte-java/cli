/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.nio.file.Path

/**
 * Settings class that defines the settings used by the Java plugin.
 */
class JavaSettings {
  /**
   * Configures the Java version to use for compilation. This version must be defined in the
   * ~/.config/latte/plugins/org.lattejava.plugin.java.properties file.
   */
  String javaVersion

  /**
   * Additional compiler arguments. This are included when javac is invoked. Defaults to {@code "-g"}.
   */
  String compilerArguments = "-g"

  /**
   * Additional JavaDoc arguments. This are included when javadoc is invoked. Defaults to {@code ""}.
   */
  String docArguments = ""

  /**
   * Additional directories that contain JAR files to include in the compilation classpath. Defaults to {@code []}.
   */
  List<Path> libraryDirectories = []

  /**
   * The list of dependencies to include on the classpath when javac is called to compile the main Java source files.
   * This defaults to:
   * <p>
   * <pre>
   *   [
   *     [group: "compile", transitive: false, fetchSource: false],
   *     [group: "provided", transitive: false, fetchSource: false]
   *   ]
   * </pre>
   */
  List<Map<String, Object>> mainDependencies = [
      [group: "compile", transitive: false, fetchSource: false],
      [group: "provided", transitive: false, fetchSource: false]
  ]

  /**
   * Enables JPMS module build mode. When true, compilation uses {@code --module-path} instead of {@code -classpath},
   * and test compilation uses {@code --patch-module} to inject test classes into the main module.
   * <p>
   * If left {@code null}, auto-detected lazily on first plugin method call from the presence of
   * {@code module-info.java} in {@link JavaLayout#mainSourceDirectory}. Explicitly set this to
   * {@code true} or {@code false} in {@code project.latte} to override auto-detection.
   */
  Boolean moduleBuild

  /**
   * Enables separate test module mode. When true, src/test/java is compiled as an independent
   * JPMS module that {@code requires} the main module, rather than being patched into it.
   * <p>
   * If left {@code null}, auto-detected lazily on first plugin method call from the presence of
   * {@code module-info.java} in {@link JavaLayout#testSourceDirectory}. Requires {@link #moduleBuild}
   * to also be true; if it is not, compilation fails with an error.
   */
  Boolean testModuleBuild

  /**
   * The list of dependencies to include on the classpath when java is called to compile the test Java source files.
   * This defaults to:
   * <p>
   * <pre>
   *   [
   *     [group: "compile", transitive: false, fetchSource: false],
   *     [group: "test-compile", transitive: false, fetchSource: false],
   *     [group: "provided", transitive: false, fetchSource: false]
   *   ]
   * </pre>
   */
  List<Map<String, Object>> testDependencies = [
      [group: "compile", transitive: false, fetchSource: false],
      [group: "test-compile", transitive: false, fetchSource: false],
      [group: "provided", transitive: false, fetchSource: false]
  ]
}
