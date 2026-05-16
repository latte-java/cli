/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.file

import org.lattejava.cli.domain.Project
import org.lattejava.io.Copier
import org.lattejava.io.FileTools
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.RuntimeFailureException

/**
 * Delegate for the copy method's closure. This passes through everything to the Copier.
 *
 * @author Brian Pontarelli
 */
class CopyDelegate extends BaseFileDelegate {
  public static final String ERROR_MESSAGE = "The file plugin copy method must be called like this:\n\n" +
      "  file.copy(to: \"some dir\") {\n" +
      "    fileSet(dir: \"some other dir\")\n" +
      "    filter(token: \"%TOKEN%\", value: \"value\")\n" +
      "  }"

  public final Copier copier

  CopyDelegate(Project project, Map<String, Object> attributes) {
    super(project)

    if (!GroovyTools.attributesValid(attributes, ["to"], ["to"], [:])) {
      throw new RuntimeFailureException(ERROR_MESSAGE);
    }

    def to = FileTools.toPath(attributes["to"])
    this.copier = new Copier(project.directory.resolve(to))
  }

  /**
   * Adds a fileSet:
   *
   * <pre>
   *   fileSet(dir: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   * @return The Copier.
   */
  Copier fileSet(Map<String, Object> attributes) {
    copier.fileSet(toFileSet(attributes))
    return copier
  }

  /**
   * Adds a filter:
   *
   * <pre>
   *   filter(token: "%TOKEN%", value: "value")
   * </pre>
   *
   * @param attributes The named attributes (token and value are required).
   * @return The Copier.
   */
  Copier filter(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["token", "value"], ["token", "value"], [:])) {
      throw new RuntimeFailureException(ERROR_MESSAGE)
    }

    copier.filter(attributes["token"].toString(), attributes["value"].toString())
    return copier
  }

  /**
   * Adds an optional fileSet:
   *
   * <pre>
   *   optionalFileSet(dir: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   * @return The Copier.
   */
  Copier optionalFileSet(Map<String, Object> attributes) {
    copier.optionalFileSet(toOptionalFileSet(attributes))
    return copier
  }
}
