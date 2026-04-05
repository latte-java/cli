/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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
package org.lattejava.cli.runtime;

import java.nio.file.Path;

import org.lattejava.dep.LicenseException;
import org.lattejava.dep.PublishException;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.domain.VersionException;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.cli.plugin.PluginLoadException;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

/**
 * Runs the CLI starting from a project file.
 *
 * @author Brian Pontarelli
 */
public interface Runner {
  /**
   * Loads the given project file and executes the given targets in it.
   *
   * @param projectFile          The project file.
   * @param runtimeConfiguration The runtime configuration.
   * @throws ArtifactMetaDataMissingException If any dependencies of the project are missing an AMD file in the
   *                                          repository or local cache.
   * @throws ArtifactMissingException         If any dependencies of the project are missing in the repository or local
   *                                          cache.
   * @throws RunException                     If the CLI cannot be run (internally, not due to a failure of the runtime
   *                                          itself).
   * @throws RuntimeFailureException          If the project fails while running.
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
  void run(Path projectFile, RuntimeConfiguration runtimeConfiguration) throws ArtifactMetaDataMissingException, ArtifactMissingException,
      RunException, RuntimeFailureException, CompatibilityException, CyclicException, LicenseException, ChecksumException,
      ParseException, PluginLoadException, ProcessFailureException, PublishException, VersionException;
}
