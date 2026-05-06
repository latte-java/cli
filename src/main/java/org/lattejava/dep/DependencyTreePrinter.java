/*
 * Copyright (c) 2015-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.lattejava.dep.graph.DependencyGraph;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.lattejava.output.Output;

/**
 * Outputs a dependency tree and can optionally highlight different dependencies.
 *
 * @author Brian Pontarelli
 */
public final class DependencyTreePrinter {
  public static void print(Output output, DependencyGraph graph, Set<Dependency> highlight, Set<Dependency> bold) {
    List<Boolean> lasts = new ArrayList<>();
    graph.versionCorrectTraversal((origin, destination, edge, depth, isLast) -> {
      while (lasts.size() >= depth) {
        lasts.remove(lasts.size() - 1);
      }

      lasts.add(isLast);

      for (int i = 0; i < depth - 1; i++) {
        if (!lasts.get(i)) {
          output.info("|");
        }
        output.info("\t");
      }

      if (isLast) {
        output.info("\\-");
      } else {
        output.info("|-");
      }

      if (bold != null && bold.contains(destination)) {
        output.error("[" + destination.id + ":" + edge.dependencyVersion + "]");
      } else if (highlight != null && highlight.contains(destination)) {
        output.warning("[" + destination.id + ":" + edge.dependencyVersion + "]");
      } else {
        output.info("[" + destination.id + ":" + edge.dependencyVersion + "]");
      }

      output.infoln("");

      return true;
    });

  }
}
