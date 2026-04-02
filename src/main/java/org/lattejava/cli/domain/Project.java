/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
package org.lattejava.cli.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.DefaultDependencyService;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.graph.ArtifactGraph;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.domain.Version;
import org.lattejava.output.Output;
import org.lattejava.cli.plugin.Plugin;
import org.lattejava.util.Graph;
import org.lattejava.util.LattePaths;

/**
 * This class defines the project.
 *
 * @author Brian Pontarelli
 */
public class Project {
  public static final Object GRAPH_EDGE = new Object();

  public final DefaultDependencyService dependencyService;

  public final Path directory;

  public final List<License> licenses = new ArrayList<>();

  public final Output output;

  public final Map<String, Target> targets = new HashMap<>();

  public ArtifactGraph artifactGraph;

  public Dependencies dependencies;

  public String group;

  public String name;

  public Path pluginConfigurationDirectory = LattePaths.get().configDir().resolve("plugins");

  public Map<Artifact, Plugin> plugins = new HashMap<>();

  public Publications publications = new Publications();

  public PublishWorkflow publishWorkflow;

  public Graph<Target, Object> targetGraph;

  public Version version;

  public Workflow workflow;

  public Project(Path directory, Output output) {
    this.directory = directory;
    this.output = output;
    this.dependencyService = new DefaultDependencyService(output);
  }

  /**
   * Converts this project into an Artifact. This artifact uses the project's name for the item name and it has a type
   * of {@code jar}.
   *
   * @return The project artifact.
   */
  public ReifiedArtifact toArtifact() {
    return new ReifiedArtifact(new ArtifactID(group, name, name, "jar"), version, licenses);
  }
}
