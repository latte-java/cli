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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.lattejava.cli.command.Command;
import org.lattejava.cli.command.InitCommand;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.cli.parser.ProjectFileParser;
import org.lattejava.cli.plugin.PluginLoadException;
import org.lattejava.dep.LicenseException;
import org.lattejava.dep.PublishException;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.domain.VersionException;
import org.lattejava.output.Output;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

/**
 * Default runner. This handles global command dispatch, project file parsing, and target execution.
 * <p>
 * Global commands (like {@code init}) can run without a project file. If a project file exists and defines a target
 * that matches a global command name, the target takes precedence and a warning is printed.
 *
 * @author Brian Pontarelli
 */
public class DefaultRunner implements Runner {
  public static final Map<String, Command> COMMANDS = Map.of(
      "init", new InitCommand()
  );

  private final Output output;

  private final ProjectFileParser projectFileParser;

  private final ProjectRunner projectRunner;

  public DefaultRunner(Output output, ProjectFileParser projectFileParser, ProjectRunner projectRunner) {
    this.output = output;
    this.projectFileParser = projectFileParser;
    this.projectRunner = projectRunner;
  }

  @Override
  public void run(Path projectDir, RuntimeConfiguration runtimeConfiguration)
      throws ArtifactMetaDataMissingException, ArtifactMissingException,
      RunException, RuntimeFailureException, CompatibilityException, CyclicException, LicenseException, ChecksumException,
      ParseException, PluginLoadException, ProcessFailureException, PublishException, VersionException {
    if (runtimeConfiguration.printVersion) {
      Main.printVersion(output);
      return;
    }

    Path projectFile = projectDir.resolve("project.latte");
    boolean hasProjectFile = Files.isRegularFile(projectFile) && Files.isReadable(projectFile);

    // No project file — handle commands, help, version, or error
    if (!hasProjectFile) {
      if (runtimeConfiguration.command != null) {
        dispatchCommand(runtimeConfiguration);
        return;
      } else if (runtimeConfiguration.help) {
        Main.printHelp(output);
        return;
      } else {
        throw new RunException("Project file [project.latte] is missing or not readable.");
      }
    }

    // Project file exists — parse it
    Project project = projectFileParser.parse(projectFile, runtimeConfiguration);

    if (runtimeConfiguration.help) {
      printHelp(project);
      return;
    } else if (runtimeConfiguration.listTargets) {
      printTargets(project);
      return;
    }

    // If a global command was specified, check if the project overrides it with a target
    if (runtimeConfiguration.command != null) {
      if (project.targets.containsKey(runtimeConfiguration.command)) {
        runtimeConfiguration.targets.addFirst(runtimeConfiguration.command);
      } else {
        dispatchCommand(runtimeConfiguration);
        return;
      }
    }

    projectRunner.run(project, runtimeConfiguration.targets);
  }

  private void dispatchCommand(RuntimeConfiguration runtimeConfiguration) {
    Command command = COMMANDS.get(runtimeConfiguration.command);
    if (command != null) {
      command.run(runtimeConfiguration, output);
    }
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
