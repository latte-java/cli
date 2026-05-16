/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.lattejava.dep.DependencyService
import org.lattejava.dep.graph.ResolvedArtifactGraph
import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeFailureException

/**
 * Resolve delegate for closures.
 *
 * @author Brian Pontarelli
 */
class ResolveDelegate extends BaseDependencyDelegate {
  private final DependencyService dependencyService

  ResolveDelegate(Project project, DependencyService dependencyService) {
    super(project)
    this.dependencyService = dependencyService
  }

  /**
   * Converts the delegate to a {@link ResolvedArtifactGraph} instance.
   *
   * @return The ResolvedArtifactGraph.
   */
  ResolvedArtifactGraph resolve() {
    if (project.artifactGraph == null || project.workflow == null || traversalRules == null || traversalRules.rules.isEmpty()) {
      throw new RuntimeFailureException("Unable to resolve the project dependencies because one of these items was not specified: " +
          "[project.artifactGraph], [project.workflow], [resolveConfiguration], [resolveConfiguration.groupConfigurations]. " +
          "These are often supplied by by a closure like this:\n\n" +
          "  resolve() {\n" +
          "    dependencies(group: \"compile\", transitive: true, fetchSource: false, transitiveGroups: [\"compile\"])\n" +
          "  }")
    }

    return dependencyService.resolve(project.artifactGraph, project.workflow, traversalRules)
  }
}
