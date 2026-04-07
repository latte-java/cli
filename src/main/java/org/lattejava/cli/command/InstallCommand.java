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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.lattejava.cli.domain.Project;
import org.lattejava.cli.parser.groovy.GroovySourceTools;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.output.Output;

/**
 * Installs a dependency by adding it to the {@code project.latte} file and downloading the artifact and source JAR.
 * <p>
 * The dependency is added to the in-memory {@link Dependencies} object, then the entire {@code dependencies} block in
 * the project file is regenerated from that object. This avoids brittle string insertion.
 * <p>
 * Usage:
 * <pre>
 *   latte install org.foo:example:1.0          # adds to "compile" group
 *   latte install org.foo:example:1.0 test     # adds to "test" group (shorthand for test-compile)
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
      throw new RuntimeFailureException("Usage: latte install <group:name:version> [dependency-group]\n\nExample: latte install org.foo:example:1.0 test");
    }

    String dependencySpec = args.getFirst();
    String groupName = args.size() > 1 ? args.get(1) : "compile";

    // Validate the dependency spec parses correctly
    Artifact artifact;
    try {
      artifact = new Artifact(dependencySpec);
    } catch (Exception e) {
      throw new RuntimeFailureException("Invalid dependency [" + dependencySpec + "]. Expected format: group:name:version");
    }

    // Initialize dependencies if the project doesn't have any yet
    if (project.dependencies == null) {
      project.dependencies = new Dependencies();
    }

    // Check if the dependency already exists
    DependencyGroup group = project.dependencies.groups.get(groupName);
    if (group != null && group.dependencies.stream().anyMatch(dep -> dep.id.equals(artifact.id))) {
      output.infoln("Dependency [%s] already exists in the [%s] group.", dependencySpec, groupName);
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
    replaceDependenciesBlock(projectFile, project.dependencies);
    output.infoln("Added [%s] to [%s] group in project.latte", dependencySpec, groupName);
  }

  private void replaceDependenciesBlock(Path projectFile, Dependencies dependencies) {
    String content;
    try {
      content = Files.readString(projectFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to read project.latte: " + e.getMessage());
    }

    GroovySourceTools.Block depsBlock = GroovySourceTools.findBlock(content, "dependencies", 1);

    if (depsBlock != null) {
      // Determine the indentation of the dependencies keyword
      int lineStart = content.lastIndexOf('\n', depsBlock.start()) + 1;
      String indent = content.substring(lineStart, depsBlock.start());

      // Replace the old block with a freshly generated one
      String newBlock = generateDependenciesBlock(dependencies, indent);
      content = content.substring(0, depsBlock.start()) + newBlock + content.substring(depsBlock.end());
    } else {
      // No dependencies block — insert one inside the project block
      GroovySourceTools.Block projectBlock = GroovySourceTools.findBlock(content, "project", 0);
      if (projectBlock == null) {
        throw new RuntimeFailureException("Could not find a project block in project.latte.");
      }

      // Determine the indentation inside the project block
      int lineStart = content.lastIndexOf('\n', projectBlock.start()) + 1;
      String outerIndent = content.substring(lineStart, projectBlock.start());
      String indent = outerIndent + "  ";

      // Insert before the closing brace of the project block
      int insertPos = projectBlock.end() - 1;
      String newBlock = "\n" + indent + generateDependenciesBlock(dependencies, indent) + "\n";
      content = content.substring(0, insertPos) + newBlock + content.substring(insertPos);
    }

    try {
      Files.writeString(projectFile, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to write project.latte: " + e.getMessage());
    }
  }

  private String generateDependenciesBlock(Dependencies dependencies, String indent) {
    StringBuilder sb = new StringBuilder();
    sb.append("dependencies {\n");

    for (DependencyGroup group : dependencies.groups.values()) {
      sb.append(indent).append("  group(name: \"").append(group.name).append("\"");
      if (!group.export) {
        sb.append(", export: false");
      }
      sb.append(") {\n");

      for (Artifact dep : group.dependencies) {
        sb.append(indent).append("    dependency(id: \"").append(dep.toShortestString()).append("\")\n");
      }

      sb.append(indent).append("  }\n");
    }

    sb.append(indent).append("}");
    return sb.toString();
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
