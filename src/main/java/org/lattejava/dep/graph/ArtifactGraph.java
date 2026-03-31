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
package org.lattejava.dep.graph;

import java.util.Formatter;

import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.util.Graph.EdgeFilter.SingleTraversalEdgeFilter;
import org.lattejava.util.HashGraph;

/**
 * This class is an artifact graph that stores the relationship between artifacts. After a DependencyGraph has been
 * built, the process of upgrading all dependencies to a single version and pruning unused dependencies results in an
 * ArtifactGraph.
 *
 * @author Brian Pontarelli
 */
public class ArtifactGraph extends HashGraph<ReifiedArtifact, String> {
  public final ReifiedArtifact root;

  public ArtifactGraph(ReifiedArtifact root) {
    this.root = root;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final ArtifactGraph that = (ArtifactGraph) o;
    return root.equals(that.root);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }

  /**
   * Outputs this DependencyGraph as a GraphViz DOT file.
   *
   * @return The DOT file String.
   */
  public String toDOT() {
    StringBuilder build = new StringBuilder();
    build.append("digraph ArtifactGraph {\n");

    Formatter formatter = new Formatter(build);
    traverse(root, false, new SingleTraversalEdgeFilter<>(), (origin, destination, edge, depth, isLast) -> {
      formatter.format("  \"%s\" -> \"%s\" [label=\"%s\"];\n", origin, destination, edge);
      return true;
    });

    build.append("}\n");
    return build.toString();
  }

  public String toString() {
    return toDOT();
  }
}
