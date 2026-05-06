/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

import java.nio.file.Path;

import org.lattejava.cli.parser.ParseException;
import org.lattejava.cli.plugin.PluginLoadException;
import org.lattejava.dep.LicenseException;
import org.lattejava.dep.PublishException;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.domain.VersionException;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

/**
 * Runs the CLI. This handles global commands, project file parsing, and target execution.
 *
 * @author Brian Pontarelli
 */
public interface Runner {
  /**
   * Runs the CLI from the given project directory. This handles global command dispatch, project file parsing, and
   * target execution. If a global command is specified and no project file exists, the command is executed directly. If
   * a project file exists and defines a target matching a global command name, the target takes precedence.
   *
   * @param projectDir           The project directory (containing {@code project.latte} if it exists).
   * @param runtimeConfiguration The runtime configuration.
   */
  void run(Path projectDir, RuntimeConfiguration runtimeConfiguration) throws ArtifactMetaDataMissingException, ArtifactMissingException,
      RunException, RuntimeFailureException, CompatibilityException, CyclicException, LicenseException, ChecksumException,
      ParseException, PluginLoadException, ProcessFailureException, PublishException, VersionException;
}
