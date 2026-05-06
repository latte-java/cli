/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

import org.lattejava.dep.LicenseException;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.cli.domain.Project;
import org.lattejava.domain.VersionException;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

/**
 * Runs the project's targets using the {@link Project} and the commands from the user.
 *
 * @author Brian Pontarelli
 */
public interface ProjectRunner {
  /**
   * Executes the given targets on the given project.
   *
   * @param project The project.
   * @param targets The targets to run.
   * @throws ArtifactMetaDataMissingException If any dependencies of the project are missing an AMD file in the
   *                                          repository or local cache.
   * @throws ArtifactMissingException         If any dependencies of the project are missing in the repository or local
   *                                          cache.
   * @throws RunException                     If the build can not be run (internally not due to a failure of the build
   *                                          itself).
   * @throws RuntimeFailureException          If the build fails while running.
   * @throws CompatibilityException           If the project has incompatible versions of a dependency.
   * @throws CyclicException                  If the project has cyclic dependencies.
   * @throws LicenseException                 If the project has a dependency with an invalid license.
   * @throws ChecksumException                If a dependency is corrupt.
   * @throws ProcessFailureException          If the downloading of a dependency fails.
   * @throws VersionException                 If any of the versions are not semantic.
   */
  void run(Project project, Iterable<String> targets) throws ArtifactMetaDataMissingException, ArtifactMissingException,
      RunException, RuntimeFailureException, CompatibilityException, CyclicException, LicenseException, ChecksumException,
      ParseException, ProcessFailureException, VersionException;
}
