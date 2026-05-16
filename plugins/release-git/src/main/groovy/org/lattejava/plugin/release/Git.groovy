/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.release

import java.nio.file.Path

import static org.lattejava.lang.RuntimeTools.ProcessResult

/**
 * Wrap for git commands. This executes git directly and does not use a Git library. Therefore, Git must be in the users
 * PATH.
 *
 * @author Brian Pontarelli
 */
class Git {
  private final Path projectDirectory

  Git(Path projectDirectory) {
    this.projectDirectory = projectDirectory
  }

  /**
   * Performs a "git pull" command and returns the Process. This waits for the process to complete. The caller can check
   * the Process for the exit code and any output.
   *
   * @return The ProcessResult.
   */
  ProcessResult pull() {
    return exec("git", "pull")
  }

  /**
   * Performs a "git status" command and returns the Process. This waits for the process to complete. The caller can check
   * the Process for the exit code and any output.
   *
   * @return The ProcessResult.
   */
  ProcessResult status(options) {
    return exec("git", "status", options)
  }

  /**
   * Fetches the new tags from the remote repository by performing a "git fetch -t". This waits for the process to
   * complete. The caller can check the Process for the exit code and any output.
   *
   * @return The ProcessResult.
   */
  ProcessResult fetchTags() {
    return exec("git", "fetch", "-t")
  }

  /**
   * Determines if the given tag exists in the local repository.
   *
   * @param tagName The tag name.
   * @return True if the tag exists, false if it doesn't.
   * @throws RuntimeException If the "git tag -l" command fails.
   */
  boolean doesTagExist(tagName) throws RuntimeException {
    ProcessResult result = exec("git", "tag", "-l", tagName.toString())
    if (result.exitCode != 0) {
      throw new RuntimeException("Unable to list the git tags.")
    }

    return result.output != null && result.output.length() > 0
  }

  /**
   * Creates a remote tag. This is a two part process and if the first step (create the local tag) fails, then this
   * throws a RuntimeException.
   *
   * @param tagName The tag to create.
   * @param comment The comment used in the commit for the tag.
   */
  void tag(tagName, comment) throws RuntimeException {
    ProcessResult result = exec("git", "tag", "-a", tagName.toString(), "-m", comment)
    if (result.exitCode != 0) {
      throw new RuntimeException("Unable to create the tag [${tagName}] in the local git repository. Exit code [${result.exitCode}]. Output is [${result.output}].")
    }

    result = exec("git", "push", "--tags")
    if (result.exitCode != 0) {
      throw new RuntimeException("Unable to push the tag [${tagName}] in the remote git repository. Exit code [${result.exitCode}]. Output is [${result.output}].")
    }
  }

  private ProcessResult exec(String... command) {
    Process process = new ProcessBuilder(command)
        .directory(projectDirectory.toFile())
        .redirectErrorStream(true)
        .start()

    InputStream is = process.getInputStream()
    byte[] buf = new byte[1024]
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024)
    int length
    while ((length = is.read(buf)) != -1) {
      baos.write(buf, 0, length)
    }

    return new ProcessResult(process.waitFor(), baos.toString("UTF-8"))
  }
}
