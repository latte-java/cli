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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import org.lattejava.cli.domain.Project
import org.lattejava.io.FileSet
import org.lattejava.io.FileTools
import org.lattejava.cli.parser.groovy.GroovyTools

/**
 * Delegate for the append method.
 *
 * @author Brian Pontarelli
 */
class AppendDelegate extends BaseFileDelegate {
  public static final String ERROR_MESSAGE = "The file plugin [append] method must be called like this:\n\n" +
      "  file.append(to: \"foo.txt\") {\n" +
      "    fileSet(dir: \"some other dir with files\")" +
      "  }"

  public final List<FileSet> fileSets = new ArrayList<>()

  public final Path to

  AppendDelegate(Project project, Map<String, Object> attributes) {
    super(project)

    if (!GroovyTools.attributesValid(attributes, ["to"], ["to"], [:])) {
      fail("You must supply a [to] attribute to the append method. Like this:\n\n" +
          "  file.append(to: \"build/somefile.txt\") {\n" +
          "    fileSet(dir: \"some-dir\")\n" +
          "  }");
    }

    to = project.directory.resolve(FileTools.toPath(attributes["to"]))
    if (Files.notExists(to)) {
      Files.createDirectories(to.getParent())
      Files.createFile(to)
    }
  }

  /**
   * Performs the append operation.
   *
   * @return The number of files appended.
   */
  int append() {
    int count = 0
    Files.newOutputStream(to, StandardOpenOption.APPEND, StandardOpenOption.WRITE).withStream { os ->
      fileSets.each { fileSet ->
        fileSet.toFileInfos().each { info ->
          Files.copy(info.origin, os)
          count++
        }
      }
    }

    return count
  }

  /**
   * Adds a fileSet:
   *
   * <pre>
   *   fileSet(dir: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  void fileSet(Map<String, Object> attributes) {
    fileSets.add(toFileSet(attributes))
  }

  /**
   * Adds an optional fileSet:
   *
   * <pre>
   *   optionalFileSet(dir: "someDir")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  void optionalFileSet(Map<String, Object> attributes) {
    FileSet fileSet = toOptionalFileSet(attributes)
    if (Files.isDirectory(fileSet.directory)) {
      fileSets.add(fileSet)
    }
  }
}
