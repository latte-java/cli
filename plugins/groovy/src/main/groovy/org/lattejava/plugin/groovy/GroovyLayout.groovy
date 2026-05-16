/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.groovy

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Layout class that defines the directories used by the Groovy plugin.
 */
class GroovyLayout {
  /**
   * The build directory. Defaults to {@code build}.
   */
  Path buildDirectory = Paths.get("build")

  /**
   * The documentation directory. Defaults to {@code build/doc}.
   */
  Path docDirectory = buildDirectory.resolve("doc")

  /**
   * The jar build directory. Defaults to {@code build/jars}.
   */
  Path jarOutputDirectory = buildDirectory.resolve("jars")

  /**
   * The main source directory. Defaults to {@code src/main/groovy}.
   */
  Path mainSourceDirectory = Paths.get("src/main/groovy")

  /**
   * The main resource directory. Defaults to {@code src/main/resources}.
   */
  Path mainResourceDirectory = Paths.get("src/main/resources")

  /**
   * The main build directory. Defaults to {@code build/classes/main}.
   */
  Path mainBuildDirectory = buildDirectory.resolve("classes/main")

  /**
   * The test source directory. Defaults to {@code src/test/groovy}.
   */
  Path testSourceDirectory = Paths.get("src/test/groovy")

  /**
   * The test resource directory. Defaults to {@code src/test/resources}.
   */
  Path testResourceDirectory = Paths.get("src/test/resources")

  /**
   * The test build directory. Defaults to {@code build/classes/test}.
   */
  Path testBuildDirectory = buildDirectory.resolve("classes/test")
}
