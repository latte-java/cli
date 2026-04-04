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

import org.lattejava.cli.domain.Project
import org.lattejava.io.ArchiveFileSet
import org.lattejava.io.Directory
import org.lattejava.io.FileSet
import org.lattejava.io.FileTools
import org.lattejava.cli.runtime.BuildFailureException

/**
 * Base class for file delegate classes.
 *
 * @author Brian Pontarelli
 */
class BaseFileDelegate {
  protected final Project project

  BaseFileDelegate(Project project) {
    this.project = project
  }

  protected ArchiveFileSet toArchiveFileSet(Map<String, Object> attributes) {
    String error = ArchiveFileSet.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    Path dir = project.directory.resolve(FileTools.toPath(attributes["dir"]))
    FileSet fileSet = ArchiveFileSet.fromAttributes(dir, attributes)
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    if (!Files.isDirectory(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] does not exist");
    }

    return fileSet
  }

  protected Directory toDirectory(Map<String, Object> attributes) {
    String error = Directory.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    return Directory.fromAttributes(attributes)
  }

  protected FileSet toFileSet(Map<String, Object> attributes) {
    String error = FileSet.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    Path dir = project.directory.resolve(FileTools.toPath(attributes["dir"]))
    FileSet fileSet = FileSet.fromAttributes(dir, attributes)
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    if (!Files.isDirectory(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] does not exist");
    }

    return fileSet
  }

  protected FileSet toOptionalFileSet(Map<String, Object> attributes) {
    String error = FileSet.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    Path dir = project.directory.resolve(FileTools.toPath(attributes["dir"]))
    FileSet fileSet = FileSet.fromAttributes(dir, attributes)
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    return fileSet
  }
}
