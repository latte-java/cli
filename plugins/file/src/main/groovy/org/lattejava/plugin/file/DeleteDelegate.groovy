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

import org.lattejava.cli.domain.Project
import org.lattejava.io.FileSet

/**
 * Delegate for the delete method's closure. This uses FileSets to delete 0 or more files.
 *
 * @author Brian Pontarelli
 */
class DeleteDelegate extends BaseFileDelegate {
  public static final String ERROR_MESSAGE = "The file plugin [delete] method must be called like this:\n\n" +
      "  file.delete {\n" +
      "    fileSet(dir: \"some other dir\")\n" +
      "  }"

  public final List<FileSet> fileSets = new ArrayList<>()

  DeleteDelegate(Project project) {
    super(project)
  }

  /**
   * Deletes the files specified by the FileSets.
   *
   * @return The number of files deleted.
   */
  int delete() {
    int count = 0
    fileSets.each { fileSet ->
      fileSet.toFileInfos().each { info ->
        if (Files.deleteIfExists(info.origin)) {
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
