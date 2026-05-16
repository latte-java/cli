/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.file

import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.io.jar.JarBuilder
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.RuntimeFailureException

/**
 * Delegate for the jar method's closure. This passes through everything to the JarBuilder.
 *
 * @author Brian Pontarelli
 */
class JarDelegate extends BaseFileDelegate {
  public static final String ERROR_MESSAGE = "The file plugin jar method must be called like this:\n\n" +
      "  file.jar(file: \"file.jar\") {\n" +
      "    fileSet(dir: \"some other dir\")\n" +
      "    directory(name: \"some dir\")\n" +
      "    manifest(file: \"some file\")\n" +
      "  }"

  public final JarBuilder builder

  JarDelegate(Project project, Map<String, Object> attributes) {
    super(project)

    if (!GroovyTools.attributesValid(attributes, ["file"], ["file"], [:])) {
      throw new RuntimeFailureException(ERROR_MESSAGE);
    }

    def file = FileTools.toPath(attributes["file"])
    this.builder = new JarBuilder(project.directory.resolve(file))
  }

  /**
   * Adds a directory to the JAR file:
   * <p>
   * <pre>
   *   directory(name: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (name is required).
   */
  JarBuilder directory(Map<String, Object> attributes) {
    builder.directory(toDirectory(attributes))
    return builder
  }

  /**
   * Adds a fileSet:
   *
   * <pre>
   *   fileSet(dir: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   * @return The JarBuilder.
   */
  JarBuilder fileSet(Map<String, Object> attributes) {
    builder.fileSet(toFileSet(attributes))
    return builder
  }

  /**
   * Adds a MANIFEST.MF file to the jar. This can specify a file for the manifest or it can specify the manifest using a
   * Map of values. Here are some examples:
   * <p>
   * <pre>
   *   manifest(file: "src/main/META-INF/MANIFEST.MF")
   * </pre>
   * <p>
   * <pre>
   *   manifest(map: [
   *     "Implementation-Version": project.version
   *   ])
   * </pre>
   *
   * @param attributes The named attributes.
   * @return The JarBuilder
   */
  JarBuilder manifest(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["file", "map"], [], ["map": Map.class])) {
      throw new RuntimeFailureException("Invalid manifest directive ${attributes.keySet()}. ${ERROR_MESSAGE}")
    }

    if (attributes.containsKey("file")) {
      builder.manifest(project.directory.resolve(FileTools.toPath(attributes["file"])))
    } else if (attributes.containsKey("map")) {
      builder.manifest(attributes["map"])
    }
    return builder
  }

  /**
   * Adds an optionalFileSet:
   *
   * <pre>
   *   optionalFileSet(dir: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   * @return The JarBuilder.
   */
  JarBuilder optionalFileSet(Map<String, Object> attributes) {
    builder.optionalFileSet(toOptionalFileSet(attributes))
    return builder
  }
}
