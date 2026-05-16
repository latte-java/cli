/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
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
   * ~/.config/latte/plugins/org.lattejava.plugin.groovy.properties file.
   */
  String groovyVersion

  /**
   * Configures the Java version to use when running the tests. This version must be defined in the
   * ~/.config/latte/plugins/org.lattejava.plugin.java.properties file.
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
