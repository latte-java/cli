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
package org.lattejava.plugin.file

import org.lattejava.cli.domain.Project
import org.lattejava.io.Copier
import org.lattejava.io.FileTools
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.BuildFailureException

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
      throw new BuildFailureException(ERROR_MESSAGE);
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
      throw new BuildFailureException(ERROR_MESSAGE)
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
