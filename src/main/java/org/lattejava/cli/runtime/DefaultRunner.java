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
import org.lattejava.cli.domain.Project;
import org.lattejava.domain.VersionException;
import org.lattejava.output.Output;
import org.lattejava.cli.parser.ProjectFileParser;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.cli.plugin.PluginLoadException;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

/**
 * Default runner. This is essentially the main entry point for the CLI system. It takes a project file and a list
 * of targets and runs the CLI.
 * <p>
 * This implementation uses the main {@link ProjectFileParser} to parse the project file into domain objects.
 * <p>
 * Once the project file is parsed, this uses the default {@link ProjectRunner} to run build on the project.
 *
 * @author Brian Pontarelli
 */
public class DefaultRunner implements Runner {
  private final ProjectFileParser projectFileParser;

  private final Output output;

  private final ProjectRunner projectRunner;

  public DefaultRunner(Output output, ProjectFileParser projectFileParser, ProjectRunner projectRunner) {
    this.output = output;
    this.projectFileParser = projectFileParser;
    this.projectRunner = projectRunner;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run(Path projectFile, RuntimeConfiguration runtimeConfiguration)
      throws ArtifactMetaDataMissingException, ArtifactMissingException,
      RunException, RuntimeFailureException, CompatibilityException, CyclicException, LicenseException, ChecksumException,
      ParseException, PluginLoadException, ProcessFailureException, PublishException, VersionException {
    if (runtimeConfiguration.printVersion) {
      Main.printVersion(output);
      return;
    }

    Project project = projectFileParser.parse(projectFile, runtimeConfiguration);

    if (runtimeConfiguration.help) {
      printHelp(project);
      return;
    } else if (runtimeConfiguration.listTargets) {
      printTargets(project);
      return;
    }

    projectRunner.run(project, runtimeConfiguration.targets);
  }

  private void printHelp(Project project) {
    Main.printHelp(output);
    printTargets(project);
  }

  private void printTargets(Project project) {
    output.infoln("Targets in the project file:");
    output.infoln("");
    project.targets.forEach((name, target) -> {
      output.infoln("  %s: %s", name, target.description != null ? target.description : "No description");
    });
  }
}
