/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.command;

import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.output.Output;

/**
 * A built-in global command that can be invoked without a project file.
 *
 * @author Brian Pontarelli
 */
public interface Command {
  /**
   * Executes the command.
   *
   * @param configuration The runtime configuration parsed from the CLI arguments.
   * @param output        The output for user interaction and logging.
   * @param project       The parsed project, or null if no project file exists.
   */
  void run(RuntimeConfiguration configuration, Output output, Project project);
}
