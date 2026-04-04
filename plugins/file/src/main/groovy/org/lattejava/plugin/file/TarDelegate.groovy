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
import org.lattejava.io.tar.TarBuilder
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.BuildFailureException

/**
 * Delegate for the tar method's closure. This does all the work of building Tarfiles.
 *
 * @author Brian Pontarelli
 */
class TarDelegate extends BaseFileDelegate {
  public static final String ERROR_MESSAGE = "The file plugin tar method must be called like this:\n\n" +
      "  file.tar(file: \"file.tar.gz\", compress: true, storeGroupName: true, storeUserName: true) {\n" +
      "    fileSet(dir: \"some other dir\")\n" +
      "    tarFileSet(dir: \"some other dir\", prefix: \"some-prefix\")\n" +
      "  }"

  public final TarBuilder builder

  TarDelegate(Project project, Map<String, Object> attributes) {
    super(project)

    if (!GroovyTools.attributesValid(attributes, ["file", "compress"], ["file"], ["compress": Boolean.class])) {
      throw new BuildFailureException(ERROR_MESSAGE);
    }

    this.builder = new TarBuilder(project.directory.resolve(FileTools.toPath(attributes["file"])))
    if (attributes["compress"]) {
      this.builder.compress = attributes["compress"]
    }
    if (attributes["storeGroupName"]) {
      this.builder.storeGroupName = attributes["storeGroupName"]
    }
    if (attributes["storeUserName"]) {
      this.builder.storeUserName = attributes["storeUserName"]
    }
  }

  /**
   * Adds a directory to the TAR file:
   * <p>
   * <pre>
   *   directory(name: "someDir", mode: 0x755, userName: "root", groupName: "root")
   * </pre>
   *
   * @param attributes The named attributes (name is required).
   */
  TarBuilder directory(Map<String, Object> attributes) {
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
   * @return The TarBuilder.
   */
  TarBuilder fileSet(Map<String, Object> attributes) {
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
   * @return The TarBuilder.
   */
  TarBuilder optionalFileSet(Map<String, Object> attributes) {
    builder.optionalFileSet(toOptionalFileSet(attributes))
    return builder
  }

  /**
   * Adds a tarFileSet:
   *
   * <pre>
   *   tarFileSet(dir: "someDir", prefix: "some-prefix")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   * @return The TarBuilder.
   */
  TarBuilder tarFileSet(Map<String, Object> attributes) {
    builder.fileSet(toArchiveFileSet(attributes))
    return builder
  }
}
