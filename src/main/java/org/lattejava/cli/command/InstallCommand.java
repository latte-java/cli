/*
 * Copyright (c) 2026, Inversoft Inc., All Rights Reserved
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
package org.lattejava.cli.command;

import java.nio.file.Path;
import java.util.List;

import org.lattejava.cli.domain.Project;
import org.lattejava.cli.parser.groovy.ProjectFileTools;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.domain.Version;
import org.lattejava.net.RepositoryTools;
import org.lattejava.output.Output;

/**
 * Installs a dependency by adding it to the {@code project.latte} file and downloading the artifact and source JAR.
 * <p>
 * The dependency is added to the in-memory {@link Dependencies} object, then the entire {@code dependencies} block in
 * the project file is regenerated from that object. This avoids brittle string insertion.
 * <p>
 * Usage:
 * <pre>
 *   latte install org.foo:example              # resolves latest version, adds to "compile" group
 *   latte install org.foo:example 1.0          # adds to "compile" group
 *   latte install org.foo:example 1.0 test     # adds to "test" group
 * </pre>
 *
 * @author Brian Pontarelli
 */
public class InstallCommand implements Command {
  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The install command requires a project.latte file.");
    }

    List<String> args = configuration.args;
    if (args.isEmpty()) {
      throw new RuntimeFailureException("Usage: latte install <artifact-id> [version] [dependency-group]\n\nExample: latte install org.foo:example 1.0 test");
    }

    // Parse the artifact ID (no version)
    ArtifactID id;
    try {
      id = new ArtifactID(args.getFirst());
    } catch (Exception e) {
      throw new RuntimeFailureException("Invalid dependency [" + args.getFirst() + "]. Expected format: group:name");
    }

    // Version is the second arg, or resolve from the repository
    String version;
    String groupName;
    if (args.size() > 1) {
      version = args.get(1);
      groupName = args.size() > 2 ? args.get(2) : "compile";
    } else {
      output.infoln("Resolving latest version for [%s:%s]...", id.group, id.project);
      version = RepositoryTools.queryLatestVersion(id.group + ":" + id.project);
      if (version == null) {
        throw new RuntimeFailureException("Could not find artifact [" + id.group + ":" + id.project + "] in the repository.");
      }
      output.infoln("Resolved to version [%s]", version);
      groupName = "compile";
    }

    Version artifactVersion = new Version(version);
    Artifact artifact = new Artifact(id, artifactVersion);

    // Initialize dependencies if the project doesn't have any yet
    if (project.dependencies == null) {
      project.dependencies = new Dependencies();
    }

    // Check if the dependency already exists
    DependencyGroup group = project.dependencies.groups.get(groupName);
    if (group != null && group.dependencies.stream().anyMatch(dep -> dep.id.equals(artifact.id))) {
      output.infoln("Dependency [%s] already exists in the [%s] group.", artifact, groupName);
      return;
    }

    // Download the artifact first to verify it exists before modifying the project file
    downloadArtifact(artifact, project.workflow, output);

    // Add the artifact to the in-memory model
    if (group == null) {
      group = new DependencyGroup(groupName, true);
      project.dependencies.groups.put(groupName, group);
    }
    group.dependencies.add(artifact);

    // Regenerate the dependencies block in the project file
    Path projectFile = project.directory.resolve("project.latte");
    ProjectFileTools.writeDependencies(projectFile, project.dependencies);
    output.infoln("Added [%s] to [%s] group in project.latte", artifact, groupName);
  }

  private void downloadArtifact(Artifact artifact, Workflow workflow, Output output) {
    if (workflow == null) {
      return;
    }

    try {
      output.infoln("Downloading [%s]", artifact);
      workflow.fetchArtifact(artifact);
    } catch (Exception e) {
      throw new RuntimeFailureException("Artifact [" + artifact.toShortestString() + "] could not be found. " + e.getMessage());
    }

    try {
      workflow.fetchSource(artifact);
    } catch (Exception e) {
      output.debugln("Source JAR not available for [%s]: %s", artifact, e.getMessage());
    }
  }
}
