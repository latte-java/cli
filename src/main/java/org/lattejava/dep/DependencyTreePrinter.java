/*
 * Copyright (c) 2015, Inversoft Inc., All Rights Reserved
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
