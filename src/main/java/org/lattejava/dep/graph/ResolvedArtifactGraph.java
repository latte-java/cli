/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.graph;

import java.util.Formatter;

import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ResolvedArtifact;
import org.lattejava.lang.Classpath;
import org.lattejava.util.Graph.EdgeFilter.SingleTraversalEdgeFilter;
import org.lattejava.util.HashGraph;

/**
 * This class is a resolved artifact and dependency version of the Graph. The link between graph nodes is the artifact
 * group type as a String. The nodes contain the resolved artifact's, which include the Path of the artifact on the
 * local file system.
 *
 * @author Brian Pontarelli
 */
public class ResolvedArtifactGraph extends HashGraph<ResolvedArtifact, String> {
  public final ResolvedArtifact root;

  public ResolvedArtifactGraph(ResolvedArtifact root) {
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

    final ResolvedArtifactGraph that = (ResolvedArtifactGraph) o;
    return root.equals(that.root);
  }

  /**
   * Brute force traverses the graph and locates the Path for the given artifact. This only needs the ArtifactID because
   * this graph will never contain two versions of the same artifact.
   *
   * @param id The id.
   * @return The Path or null if the graph doesn't contain the given Artifact.
   */
  public java.nio.file.Path getPath(ArtifactID id) {
    ResolvedArtifact match = find(root, (artifact) -> artifact.id.equals(id));
    if (match != null) {
      return match.file;
    }

    return null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + root.hashCode();
    return result;
  }

  public Classpath toClasspath() {
    if (size() == 0) {
      return new Classpath();
    }

    Classpath classpath = new Classpath();
    traverse(root, true, null, (origin, destination, value, depth, isLast) -> {
      classpath.path(destination.file);
      return true;
    });

    return classpath;
  }

  /**
   * Outputs this DependencyGraph as a GraphViz DOT file.
   *
   * @return The DOT file String.
   */
  public String toDOT() {
    StringBuilder build = new StringBuilder();
    build.append("digraph ResolvedArtifactGraph {\n");

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
