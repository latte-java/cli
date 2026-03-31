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

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * This class is a test case for the artifact graph data structure.
 *
 * @author Brian Pontarelli
 */
public class DependencyGraphTest extends BaseUnitTest {
  @Test
  public void equals() {
    ReifiedArtifact one = new ReifiedArtifact("group:project:artifact1:1.0.0:jar", License.Licenses.get("ApacheV2_0"));
    ReifiedArtifact two = new ReifiedArtifact("group:project:artifact2:1.0.0:jar", new License());
    ReifiedArtifact three = new ReifiedArtifact("group:project:artifact3:1.0.0:jar", new License());
    ReifiedArtifact four = new ReifiedArtifact("group:project:artifact4:1.0.0:jar", new License());

    DependencyGraph graph = new DependencyGraph(one);
    graph.addEdge(new Dependency(one.id), new Dependency(two.id), new DependencyEdgeValue(one.version, two.version, "compile", new License()));
    graph.addEdge(new Dependency(one.id), new Dependency(three.id), new DependencyEdgeValue(one.version, three.version, "compile", new License()));
    graph.addEdge(new Dependency(two.id), new Dependency(four.id), new DependencyEdgeValue(two.version, four.version, "compile", new License()));
    graph.addEdge(new Dependency(three.id), new Dependency(four.id), new DependencyEdgeValue(three.version, four.version, "compile", new License()));

    DependencyGraph graph2 = new DependencyGraph(one);
    graph2.addEdge(new Dependency(three.id), new Dependency(four.id), new DependencyEdgeValue(three.version, four.version, "compile", new License()));
    graph2.addEdge(new Dependency(two.id), new Dependency(four.id), new DependencyEdgeValue(two.version, four.version, "compile", new License()));
    graph2.addEdge(new Dependency(one.id), new Dependency(three.id), new DependencyEdgeValue(one.version, three.version, "compile", new License()));
    graph2.addEdge(new Dependency(one.id), new Dependency(two.id), new DependencyEdgeValue(one.version, two.version, "compile", new License()));

    assertEquals(graph, graph2);
  }

  @Test
  public void versionCorrectTraversal() {
    ReifiedArtifact root = new ReifiedArtifact("group:project:root:1.0.0:jar", new License());
    ReifiedArtifact one = new ReifiedArtifact("group:project:artifact1:1.0.0:jar", new License());
    ReifiedArtifact two = new ReifiedArtifact("group:project:artifact1:1.1.0:jar", new License());
    ReifiedArtifact three = new ReifiedArtifact("group:project:artifact3:1.0.0:jar", new License());
    ReifiedArtifact four = new ReifiedArtifact("group:project:artifact4:1.0.0:jar", new License());

    DependencyGraph graph = new DependencyGraph(root);
    graph.addEdge(new Dependency(root.id), new Dependency(one.id), new DependencyEdgeValue(root.version, one.version, "compile", new License()));
    graph.addEdge(new Dependency(root.id), new Dependency(two.id), new DependencyEdgeValue(root.version, two.version, "compile", new License()));
    graph.addEdge(new Dependency(one.id), new Dependency(three.id), new DependencyEdgeValue(one.version, three.version, "compile", new License()));
    graph.addEdge(new Dependency(two.id), new Dependency(four.id), new DependencyEdgeValue(two.version, four.version, "compile", new License()));

    ArtifactID[] path1 = new ArtifactID[2];
    ArtifactID[] path2 = new ArtifactID[2];
    graph.versionCorrectTraversal((origin, destination, edge, depth, isLast) -> {
      ArtifactID[] path = (path1[depth - 1] == null) ? path1 : path2;
      path[depth - 1] = destination.id;
      return true;
    });

    assertEquals(path1, new ArtifactID[]{one.id, three.id});
    assertEquals(path2, new ArtifactID[]{two.id, four.id});
  }
}
