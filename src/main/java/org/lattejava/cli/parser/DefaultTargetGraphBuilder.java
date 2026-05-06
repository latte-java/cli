/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser;

import org.lattejava.util.Graph;
import org.lattejava.util.HashGraph;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.domain.Target;

/**
 * Default target graph builder.
 *
 * @author Brian Pontarelli
 */
public class DefaultTargetGraphBuilder implements TargetGraphBuilder {
  @Override
  public Graph<Target, Object> build(Project project) {
    Graph<Target, Object> graph = new HashGraph<>();
    project.targets.forEach((name, target) -> {
      if (target.dependencies == null) {
        return;
      }

      target.dependencies.forEach((dependency) -> {
        Target dependencyTarget = project.targets.get(dependency);
        if (dependencyTarget == null) {
          throw new ParseException("Invalid dependsOn for target [" + name + "]. Target [" + dependency + "] does not exist");
        }

        graph.addEdge(target, dependencyTarget, Project.GRAPH_EDGE);
      });
    });

    return graph;
  }
}
