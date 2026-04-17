/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.plugin.java.testng

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Layout class that defines the source directories used by the Java TestNG plugin for auto-detecting
 * module-info.java files. Only the source directories are exposed because the TestNG plugin does not
 * produce build output — it consumes publications built by the {@code java} plugin.
 */
class JavaLayout {
  /**
   * The main source directory. Defaults to {@code src/main/java}.
   */
  Path mainSourceDirectory = Paths.get("src/main/java")

  /**
   * The test source directory. Defaults to {@code src/test/java}.
   */
  Path testSourceDirectory = Paths.get("src/test/java")
}
