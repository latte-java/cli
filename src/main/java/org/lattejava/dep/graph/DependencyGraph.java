/*
 * Copyright (c) 2014-2024, Inversoft Inc., All Rights Reserved
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

import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.lattejava.util.Graph.EdgeFilter.SingleTraversalEdgeFilter;
import org.lattejava.util.HashGraph;

/**
 * This class is an artifact and dependency version of the Graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyGraph extends HashGraph<Dependency, DependencyEdgeValue> {
  public final ReifiedArtifact root;

  public DependencyGraph(ReifiedArtifact root) {
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

    final DependencyGraph that = (DependencyGraph) o;
    return root.equals(that.root);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }

  public void skipCompatibilityCheck(ArtifactID id) {
    HashNode<Dependency, DependencyEdgeValue> node = getNode(new Dependency(id));
    node.value.skipCompatibilityCheck = true;
  }

  /**
   * Outputs this DependencyGraph as a GraphViz DOT file.
   *
   * @return The DOT file String.
   */
  public String toDOT() {
    StringBuilder build = new StringBuilder();
    build.append("digraph Dependencies {\n");

    Formatter formatter = new Formatter(build);
    traverse(new Dependency(root.id), false, new SingleTraversalEdgeFilter<>(), (origin, destination, edge, depth, isLast) -> {
      formatter.format("  \"%s\" -> \"%s\" [label=\"%s\", headlabel=\"%s\", taillabel=\"%s\"];\n", origin, destination, edge, edge.dependentVersion, edge.dependencyVersion);
      return true;
    });

    build.append("}\n");
    return build.toString();
  }

  public String toString() {
    return toDOT();
  }

  /**
   * Traverses the dependency graph in a version consistent manner. This essentially guarantees that at any given node,
   * only the dependencies for the version of the current traversal are followed. For this graph:
   *
   * <pre>
   *   B -1.1----1.2--&gt; C -1.2----1.1--&gt; D
   *   \--1.2----1.3---/\-1.3----2.0--&gt; E
   * </pre>
   * <p>
   * If we are examining B version 1.1 once we traverse to C, we will only observe D. Likewise, if we are examining B
   * version 1.2, then we will traverse to C version 1.3 and only see E.
   *
   * @param consumer The graph consumer.
   */
  public void versionCorrectTraversal(GraphConsumer<Dependency, DependencyEdgeValue> consumer) {
    super.traverse(
        new Dependency(root.id),
        false,
        (edge, traversedEdge) -> edge.getValue().dependentVersion.equals(traversedEdge.getValue().dependencyVersion),
        consumer
    );
  }

  public static class Dependency {
    public final ArtifactID id;

    public boolean skipCompatibilityCheck;

    public Dependency(ArtifactID id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Dependency that = (Dependency) o;
      return id.equals(that.id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }

    @Override
    public String toString() {
      return "Dependency{" +
          "id=" + id +
          ", skipCompatibilityCheck=" + skipCompatibilityCheck +
          '}';
    }
  }
}
