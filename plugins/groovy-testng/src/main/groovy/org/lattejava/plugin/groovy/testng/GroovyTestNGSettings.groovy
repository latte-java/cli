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
package org.lattejava.plugin.groovy.testng

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Settings class that defines the settings used by the TestNG plugin.
 */
class GroovyTestNGSettings {
  /**
   * Configures the groovy version to use when running the tests. This version must be defined in the
   * ~/.savant/plugins/org.lattejava.plugin.groovy.properties file.
   */
  String groovyVersion

  /**
   * Configures the Java version to use when running the tests. This version must be defined in the
   * ~/.savant/plugins/org.lattejava.plugin.java.properties file.
   */
  String javaVersion

  /**
   * Any additional JVM arguments that are passed to java when the tests are run. Defaults to {@code ""}.
   */
  String jvmArguments = ""

  /**
   * Determines if invokedynamic version of Groovy should be used or not. Defaults to {@code false}.
   */
  boolean indy = false

  /**
   * Determines the verbosity of the TestNG output. Defaults to {@code 1}.
   */
  int verbosity = 1

  /**
   * Determines location that the TestNG reports are put. Defaults to {@code build/test-reports}.
   */
  Path reportDirectory = Paths.get("build/test-reports")

  /**
   * Defines the dependencies that are included on the classpath when the tests are run. This defaults to:
   * <p>
   * <pre>
   *   [
   *     [group: "provided", transitive: true, fetchSource: false],
   *     [group: "compile", transitive: true, fetchSource: false],
   *     [group: "runtime", transitive: true, fetchSource: false],
   *     [group: "test-compile", transitive: true, fetchSource: false],
   *     [group: "test-runtime", transitive: true, fetchSource: false]
   *   ]
   * </pre>
   */
  List<Map<String, Object>> dependencies = [
      [group: "provided", transitive: true, fetchSource: false],
      [group: "compile", transitive: true, fetchSource: false],
      [group: "runtime", transitive: true, fetchSource: false],
      [group: "test-compile", transitive: true, fetchSource: false],
      [group: "test-runtime", transitive: true, fetchSource: false]
  ]
}
