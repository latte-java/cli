/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.file

import java.nio.file.Files
import java.nio.file.Path

import org.lattejava.cli.domain.Project
import org.lattejava.io.ArchiveFileSet
import org.lattejava.io.Directory
import org.lattejava.io.FileSet
import org.lattejava.io.FileTools
import org.lattejava.cli.runtime.RuntimeFailureException

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
      throw new RuntimeFailureException(error)
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
      throw new RuntimeFailureException(error)
    }

    return Directory.fromAttributes(attributes)
  }

  protected FileSet toFileSet(Map<String, Object> attributes) {
    String error = FileSet.attributesValid(attributes)
    if (error != null) {
      throw new RuntimeFailureException(error)
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
      throw new RuntimeFailureException(error)
    }

    Path dir = project.directory.resolve(FileTools.toPath(attributes["dir"]))
    FileSet fileSet = FileSet.fromAttributes(dir, attributes)
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    return fileSet
  }
}
