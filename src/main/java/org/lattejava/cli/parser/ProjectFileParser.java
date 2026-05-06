/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser;

import java.nio.file.Path;

import org.lattejava.cli.domain.Project;
import org.lattejava.cli.plugin.PluginLoadException;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.cli.runtime.RunException;
import org.lattejava.cli.runtime.RuntimeConfiguration;
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
 * Parses the project file into the domain objects.
 *
 * @author Brian Pontarelli
 */
public interface ProjectFileParser {
  /**
   * Parses the given file and generates the Project object.
   *
   * @param file                 The file.
   * @param runtimeConfiguration The runtime configuration that is passed to the build script.
   * @return The Project.
   * @throws ArtifactMetaDataMissingException If any dependencies of the project are missing an AMD file in the
   *                                          repository or local cache.
   * @throws ArtifactMissingException         If any dependencies of the project are missing in the repository or local
   *                                          cache.
   * @throws RunException                     If the build cannot be run (internally, not due to a failure of the build
   *                                          itself).
   * @throws RuntimeFailureException          If the build fails while running.
   * @throws CompatibilityException           If the project has incompatible versions of a dependency.
   * @throws CyclicException                  If the project has cyclic dependencies.
   * @throws LicenseException                 If the project has a dependency with an invalid license.
   * @throws ChecksumException                If a dependency is corrupt.
   * @throws ParseException                   If the project file cannot be parsed.
   * @throws PublishException                 If there was an error publishing an artifact.
   * @throws PluginLoadException              If a plugin load failed for any reason (the plugin might not exist, might
   *                                          be invalid, or could have thrown an exception during construction because
   *                                          it was missing configuration or something.)
   * @throws ProcessFailureException          If the downloading of a dependency fails.
   * @throws VersionException                 If any of the versions are not semantic.
   */
  Project parse(Path file, RuntimeConfiguration runtimeConfiguration) throws ArtifactMetaDataMissingException,
      ArtifactMissingException, RunException, RuntimeFailureException, CompatibilityException, CyclicException,
      LicenseException, ChecksumException, ParseException, PluginLoadException, ProcessFailureException, PublishException,
      VersionException;
}
