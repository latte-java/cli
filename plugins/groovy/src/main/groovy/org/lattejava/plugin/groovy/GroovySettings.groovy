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
package org.lattejava.plugin.groovy

/**
 * Settings class that defines the settings used by the Groovy plugin.
 *
 * @author Brian Pontarelli
 */
class GroovySettings {
  /**
   * Configures the groovy version to use for compilation. This version must be defined in the
   * ~/.savant/plugins/org.lattejava.plugin.groovy.properties file.
   */
  String groovyVersion

  /**
   * Configures the Java version to use for compilation. This version must be defined in the
   * ~/.savant/plugins/org.lattejava.plugin.java.properties file.
   */
  String javaVersion

  /**
   * A list of JVM arguments that are used when invoking groovy or groovyc commands. These are passed to the environment
   * as the env variable JAVA_OPTS.
   */
  String jvmArguments = ""

  /**
   * Additional compiler arguments. This are included when groovyc is invoked. Defaults to {@code ""}.
   */
  String compilerArguments = ""

  /**
   * Additional groovydoc arguments. This are included when groovydoc is invoked. Defaults to {@code ""}.
   */
  String docArguments = ""

  /**
   * Determines if the compiler should generate code that uses invokedynamic. Defaults to {@code false}.
   */
  boolean indy = false

  /**
   * The list of dependencies to include on the classpath when groovyc is called to compile the main Groovy source
   * files. This defaults to:
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
   * The list of dependencies to include on the classpath when groovyc is called to compile the test Groovy source
   * files. This defaults to:
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

  Map<String, String> jarManifest = [:]
}
