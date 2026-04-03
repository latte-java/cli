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
package org.lattejava.dep;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.Publication;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.graph.ArtifactGraph;
import org.lattejava.dep.graph.DependencyGraph;
import org.lattejava.dep.graph.ResolvedArtifactGraph;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

/**
 * <p>
 * Provides all of the dependency management services. The main workflow for managing dependencies is:
 * </p>
 * <pre>
 *   1. Download and parse all of the AMD (AbstractArtifact Meta Data) files to build a dependency graph.
 *   2. Traverse the graph and verify that it is a valid graph (doesn't contain conflicting versions of specific
 * artifacts)
 *   3. Traverse the graph and download the dependencies
 * </pre>
 *
 * @author Brian Pontarelli
 */
public interface DependencyService {
  /**
   * Builds a dependency graph for the given dependencies of the given project.
   *
   * @param project      The artifact that represents the project.
   * @param dependencies The declared dependencies of the project.
   * @param workflow     The workflow to use for downloading and caching the AMD files.
   * @return The dependency graph.
   * @throws ArtifactMetaDataMissingException If any artifacts AMD files could not be downloaded or found locally.
   * @throws ProcessFailureException If a workflow process failed while fetching the meta-data.
   * @throws ChecksumException If any checksum files didn't match the AMD file when downloading.
   */
  DependencyGraph buildGraph(ReifiedArtifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException, ChecksumException;

  /**
   * Publishes the given Publication (artifact, meta-data, source file, etc) with the given workflow.
   *
   * @param publication The publication to publish.
   * @param workflow    The workflow to publish with.
   * @throws PublishException If the publication failed.
   */
  void publish(Publication publication, PublishWorkflow workflow) throws PublishException;

  /**
   * Reduces the DependencyGraph by ensuring that each dependency only has one version. This also prunes unused
   * dependencies and ensures there are no compatibility issues in the graph.
   *
   * @param graph The dependency graph.
   * @return The reduced graph.
   * @throws CompatibilityException If an dependency has incompatible versions.
   * @throws CyclicException If the graph has a cycle in it.
   */
  ArtifactGraph reduce(DependencyGraph graph) throws CompatibilityException, CyclicException;

  /**
   * Resolves the graph by downloading the artifacts. This will use the Workflow to download the artifacts in the graph.
   * This does not check version compatibility. That is done in the {@link #reduce(DependencyGraph)} method.
   *
   * @param graph     The ArtifactGraph to resolve.
   * @param workflow  The workflow used to resolve the artifacts.
   * @param listeners Any listeners that want to receive callbacks when artifacts are resolved.
   * @param rules     The traversal rules that are applied while traversing the graph during the reduction.
   * @return The resolved graph.
   * @throws ProcessFailureException If a workflow process failed while fetching an artifact or its source.
   * @throws ArtifactMissingException If any of the required artifacts are missing.
   * @throws CyclicException If any of the artifact graph has any cycles in it.
   * @throws ChecksumException If the item's checksum file did not match the item.
   * @throws LicenseException If an invalid license is encountered during the resolution process.
   */
  ResolvedArtifactGraph resolve(ArtifactGraph graph, Workflow workflow, TraversalRules rules, DependencyListener... listeners)
      throws CyclicException, ArtifactMissingException, ProcessFailureException, ChecksumException, LicenseException;

  /**
   * Controls how resolution functions for each dependency-group. This determines if sources are fetched or if
   * transitive dependencies are fetch.
   */
  class TraversalRules {
    public Map<String, GroupTraversalRule> rules = new HashMap<>();

    public String toString() {
      StringBuilder build = new StringBuilder();
      rules.forEach((key, value) -> {
        build.append("  [group: \"").append(key)
             .append("\", fetchSource: ").append(value.fetchSource)
             .append(", transitive: ").append(value.transitive)
             .append(", transitiveGroups: ").append(value.transitiveGroups)
             .append("]\n");
      });
      return build.toString();
    }

    public TraversalRules with(String group, GroupTraversalRule groupTraversalRule) {
      rules.put(group, groupTraversalRule);
      return this;
    }

    public static class GroupTraversalRule {
      public Set<License> disallowedLicenses = new HashSet<>();

      public boolean fetchSource;

      public boolean transitive;

      public Set<String> transitiveGroups = new HashSet<>();

      public GroupTraversalRule(boolean fetchSource, boolean transitive) {
        this.fetchSource = fetchSource;
        this.transitive = transitive;
      }

      public GroupTraversalRule(boolean fetchSource, boolean transitive, License... disallowedLicenses) {
        Collections.addAll(this.disallowedLicenses, disallowedLicenses);
        this.fetchSource = fetchSource;
        this.transitive = transitive;
      }

      public GroupTraversalRule(boolean fetchSource, String... transitiveGroups) {
        Collections.addAll(this.transitiveGroups, transitiveGroups);
        this.fetchSource = fetchSource;
        this.transitive = true;
      }

      /**
       * Construct that assists with calling from Groovy code.
       *
       * @param fetchSource      Determines if the source should be fetched.
       * @param transitive       Determines if transitive dependencies are fetched.
       * @param transitiveGroups If transitive dependencies are fetched, this controls the transitive groups that are
       *                         fetched.
       */
      public GroupTraversalRule(boolean fetchSource, boolean transitive, Collection<String> transitiveGroups) {
        this.fetchSource = fetchSource;
        this.transitive = transitive;

        if (transitive) {
          this.transitiveGroups.addAll(transitiveGroups);
        }
      }

      public GroupTraversalRule withDisallowedLicenses(License... disallowedLicenses) {
        Collections.addAll(this.disallowedLicenses, disallowedLicenses);
        return this;
      }

      public GroupTraversalRule withFetchSource(boolean fetchSource) {
        this.fetchSource = fetchSource;
        return this;
      }

      public GroupTraversalRule withTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
      }

      public GroupTraversalRule withTransitiveGroups(String... transitiveGroups) {
        Collections.addAll(this.transitiveGroups, transitiveGroups);
        return this;
      }
    }
  }
}
