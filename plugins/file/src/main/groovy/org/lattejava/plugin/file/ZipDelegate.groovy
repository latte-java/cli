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
import org.lattejava.io.FileTools
import org.lattejava.io.zip.ZipBuilder
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.BuildFailureException

/**
 * Delegate for the zip method's closure. This passes through everything to the ZipBuilder.
 *
 * @author Brian Pontarelli
 */
class ZipDelegate extends BaseFileDelegate {
  public static final String ERROR_MESSAGE = "The file plugin zip method must be called like this:\n\n" +
      "  file.zip(file: \"file.zip\") {\n" +
      "    fileSet(dir: \"some other dir\")\n" +
      "    directory(name: \"some other dir\", mode: 0x755)\n" +
      "  }"

  public final ZipBuilder builder

  ZipDelegate(Project project, Map<String, Object> attributes) {
    super(project)

    if (!GroovyTools.attributesValid(attributes, ["file"], ["file"], [:])) {
      throw new BuildFailureException(ERROR_MESSAGE);
    }

    def file = FileTools.toPath(attributes["file"])
    this.builder = new ZipBuilder(project.directory.resolve(file))
  }

  /**
   * Adds a directory to the ZIP file:
   * <p>
   * <pre>
   *   directory(name: "someDir", mode: 0x755)
   * </pre>
   *
   * @param attributes The named attributes (name is required).
   */
  ZipBuilder directory(Map<String, Object> attributes) {
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
   * @return The ZipBuilder.
   */
  ZipBuilder fileSet(Map<String, Object> attributes) {
    builder.fileSet(toFileSet(attributes))
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
   * @return The ZipBuilder.
   */
  ZipBuilder optionalFileSet(Map<String, Object> attributes) {
    builder.optionalFileSet(toOptionalFileSet(attributes))
    return builder
  }

  /**
   * Adds a zipFileSet:
   *
   * <pre>
   *   zipFileSet(dir: "someDir", prefix: "some-prefix")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  ZipBuilder zipFileSet(Map<String, Object> attributes) {
    builder.fileSet(toArchiveFileSet(attributes))
    return builder
  }
}
