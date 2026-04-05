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
  List<Object> libraryDirectories = []

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
