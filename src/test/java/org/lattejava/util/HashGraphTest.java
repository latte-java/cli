/*
 * Copyright (c) 2001-2006, Inversoft, All Rights Reserved
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
package org.lattejava.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lattejava.BaseUnitTest;
import org.lattejava.util.Graph.BasePath;
import org.lattejava.util.Graph.Edge;
import org.lattejava.util.Graph.Edge.BaseEdge;
import org.lattejava.util.Graph.Path;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * This tests the graph.
 *
 * @author Brian Pontarelli
 */
public class HashGraphTest extends BaseUnitTest {
  public HashGraph<String, String> graph;

  /**
   * Graph:
   * <p>
   * <pre>
   *   one --(one-two)--> two --(two-three)--> three --(three-five)--> five
   *    |                  |                    /\
   *    |              (two-four)               |
   *    |                  |                    |
   *    |                  \/                   |
   *    |                four                   |
   *    |                                       |
   *    |                                       |
   *    |--------------(one-three)--------------|
   * </pre>
   */
  public HashGraphTest() {
    graph = new HashGraph<>();
    graph.addEdge("one", "two", "one-two");
    graph.addEdge("two", "three", "two-three");
    graph.addEdge("one", "three", "one-three");
    graph.addEdge("two", "four", "two-four");
    graph.addEdge("three", "five", "three-five");
  }

  @Test
  public void contains() {
    assertTrue(graph.contains("one"));
    assertTrue(graph.contains("two"));
    assertTrue(graph.contains("three"));
    assertTrue(graph.contains("four"));
    assertTrue(graph.contains("five"));
    assertFalse(graph.contains("six"));
  }

  @Test
  public void equals() {
    HashGraph<String, String> graph2 = new HashGraph<>();
    graph2.addEdge("three", "five", "three-five");
    graph2.addEdge("two", "four", "two-four");
    graph2.addEdge("two", "three", "two-three");
    graph2.addEdge("one", "three", "one-three");
    graph2.addEdge("one", "two", "one-two");

    assertEquals(graph, graph2);
  }

  @Test
  public void find() {
    assertEquals(graph.find("one", (node) -> node.equals("one")), "one");
    assertEquals(graph.find("one", (node) -> node.equals("two")), "two");
    assertEquals(graph.find("one", (node) -> node.equals("three")), "three");
    assertEquals(graph.find("one", (node) -> node.equals("four")), "four");
    assertEquals(graph.find("one", (node) -> node.equals("five")), "five");
    assertNull(graph.find("one", (node) -> node.equals("six")));
    assertNull(graph.find("two", (node) -> node.equals("one")));
    assertEquals(graph.find("two", (node) -> node.equals("two")), "two");
    assertEquals(graph.find("two", (node) -> node.equals("three")), "three");
    assertEquals(graph.find("two", (node) -> node.equals("four")), "four");
    assertEquals(graph.find("two", (node) -> node.equals("five")), "five");
    assertNull(graph.find("two", (node) -> node.equals("six")));
  }

  @Test
  public void getInboundEdges() {
    List<Edge<String, String>> edges = graph.getInboundEdges("one");
    assertEquals(edges.size(), 0);

    edges = graph.getInboundEdges("two");
    assertEquals(edges.size(), 1);
    assertEquals(edges.get(0).getOrigin(), "one");
    assertEquals(edges.get(0).getDestination(), "two");
    assertEquals(edges.get(0).getValue(), "one-two");

    edges = graph.getInboundEdges("three");
    assertEquals(edges.size(), 2);
    assertEquals(edges.get(0).getOrigin(), "two");
    assertEquals(edges.get(0).getDestination(), "three");
    assertEquals(edges.get(0).getValue(), "two-three");
    assertEquals(edges.get(1).getOrigin(), "one");
    assertEquals(edges.get(1).getDestination(), "three");
    assertEquals(edges.get(1).getValue(), "one-three");

    edges = graph.getInboundEdges("four");
    assertEquals(edges.size(), 1);
    assertEquals(edges.get(0).getOrigin(), "two");
    assertEquals(edges.get(0).getDestination(), "four");
    assertEquals(edges.get(0).getValue(), "two-four");

    edges = graph.getInboundEdges("five");
    assertEquals(edges.size(), 1);
    assertEquals(edges.get(0).getOrigin(), "three");
    assertEquals(edges.get(0).getDestination(), "five");
    assertEquals(edges.get(0).getValue(), "three-five");
  }

  @Test
  public void getOutboundEdges() {
    List<Edge<String, String>> edges = graph.getOutboundEdges("one");
    assertEquals(edges.size(), 2);
    assertEquals(edges.get(0).getOrigin(), "one");
    assertEquals(edges.get(0).getDestination(), "two");
    assertEquals(edges.get(0).getValue(), "one-two");
    assertEquals(edges.get(1).getOrigin(), "one");
    assertEquals(edges.get(1).getDestination(), "three");
    assertEquals(edges.get(1).getValue(), "one-three");

    edges = graph.getOutboundEdges("two");
    assertEquals(edges.size(), 2);
    assertEquals(edges.get(0).getOrigin(), "two");
    assertEquals(edges.get(0).getDestination(), "three");
    assertEquals(edges.get(0).getValue(), "two-three");
    assertEquals(edges.get(1).getOrigin(), "two");
    assertEquals(edges.get(1).getDestination(), "four");
    assertEquals(edges.get(1).getValue(), "two-four");

    edges = graph.getOutboundEdges("three");
    assertEquals(edges.size(), 1);
    assertEquals(edges.get(0).getOrigin(), "three");
    assertEquals(edges.get(0).getDestination(), "five");
    assertEquals(edges.get(0).getValue(), "three-five");

    edges = graph.getOutboundEdges("four");
    assertEquals(edges.size(), 0);

    edges = graph.getOutboundEdges("five");
    assertEquals(edges.size(), 0);
  }

  @Test
  public void getPaths() throws Exception {
    List<Path<String>> paths = graph.getPaths("one", "five");
    assertEquals(paths.size(), 2);
    assertEquals(paths.get(0), new BasePath<>(asList("one", "two", "three", "five")));
    assertEquals(paths.get(1), new BasePath<>(asList("one", "three", "five")));
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   one --> one-two --> one-three --|
   *       |                 /\        |
   *       |                 |         |
   *       |-> two ------> four        |
   *       |    |           |          |
   *       |    |          \/          |
   *       |    |-------> five         |
   *       |    |                      |
   *       |    |-------> six <--------|
   *       |    |
   *       |   \/
   *       |-> three
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void removeEdge() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addEdge("one", "one-two", "edge");
    graph.addEdge("one-two", "one-three", "edge");
    graph.addEdge("one-three", "six", "edge");
    graph.addEdge("one", "two", "edge");
    graph.addEdge("one", "three", "edge");
    graph.addEdge("two", "three", "edge");
    graph.addEdge("two", "four", "edge");
    graph.addEdge("two", "five", "edge");
    graph.addEdge("two", "six", "edge");
    graph.addEdge("four", "five", "edge");
    graph.addEdge("four", "one-three", "edge");

    graph.removeEdge("one", "two", "edge");
    graph.removeEdge("one-three", "six", "edge");
    assertEquals(graph.size(), 8);
    assertEquals(graph.values().size(), 8);
    assertTrue(graph.contains("one"));
    assertTrue(graph.contains("one-two"));
    assertTrue(graph.contains("one-three"));
    assertTrue(graph.contains("two"));
    assertTrue(graph.contains("three"));
    assertTrue(graph.contains("four"));
    assertTrue(graph.contains("five"));
    assertTrue(graph.contains("six"));

    assertEquals(graph.getOutboundEdges("one"), asList(new BaseEdge<>("one", "one-two", "edge"), new BaseEdge<>("one", "three", "edge")));
    assertEquals(graph.getOutboundEdges("one-two"), asList(new BaseEdge<>("one-two", "one-three", "edge")));
    assertEquals(graph.getOutboundEdges("one-three"), asList());
    assertEquals(graph.getOutboundEdges("two"), asList(new BaseEdge<>("two", "three", "edge"), new BaseEdge<>("two", "four", "edge"), new BaseEdge<>("two", "five", "edge"), new BaseEdge<>("two", "six", "edge")));
    assertEquals(graph.getOutboundEdges("three"), asList());
    assertEquals(graph.getOutboundEdges("four"), asList(new BaseEdge<>("four", "five", "edge"), new BaseEdge<>("four", "one-three", "edge")));
    assertEquals(graph.getOutboundEdges("five"), asList());
    assertEquals(graph.getOutboundEdges("six"), asList());

    assertEquals(graph.getInboundEdges("one"), asList());
    assertEquals(graph.getInboundEdges("one-two"), asList(new BaseEdge<>("one", "one-two", "edge")));
    assertEquals(graph.getInboundEdges("one-three"), asList(new BaseEdge<>("one-two", "one-three", "edge"), new BaseEdge<>("four", "one-three", "edge")));
    assertEquals(graph.getInboundEdges("two"), asList());
    assertEquals(graph.getInboundEdges("three"), asList(new BaseEdge<>("one", "three", "edge"), new BaseEdge<>("two", "three", "edge")));
    assertEquals(graph.getInboundEdges("four"), asList(new BaseEdge<>("two", "four", "edge")));
    assertEquals(graph.getInboundEdges("five"), asList(new BaseEdge<>("two", "five", "edge"), new BaseEdge<>("four", "five", "edge")));
    assertEquals(graph.getInboundEdges("six"), asList(new BaseEdge<>("two", "six", "edge")));
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   one --> one-two --> one-three --|
   *       |                 /\        |
   *       |                 |         |
   *       |-> two ------> four        |
   *       |    |           |          |
   *       |    |          \/          |
   *       |    |-------> five         |
   *       |    |                      |
   *       |    |-------> six <--------|
   *       |    |
   *       |   \/
   *       |-> three
   * </pre>
   * <p>
   * Potential sub-graph to prune includes (in traversal order with duplicates):
   * <p>
   * <pre>
   *   two
   *     four
   *       one-three
   *         six
   *       five
   *     five
   *     six
   *     three
   * </pre>
   *
   * @throws Exception
   */
  @Test
  public void removeNode() throws Exception {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addEdge("one", "one-two", "edge");
    graph.addEdge("one-two", "one-three", "edge");
    graph.addEdge("one-three", "six", "edge");
    graph.addEdge("one", "two", "edge");
    graph.addEdge("one", "three", "edge");
    graph.addEdge("two", "three", "edge");
    graph.addEdge("two", "four", "edge");
    graph.addEdge("two", "five", "edge");
    graph.addEdge("two", "six", "edge");
    graph.addEdge("four", "five", "edge");
    graph.addEdge("four", "one-three", "edge");

    graph.removeNode("two");
    assertEquals(graph.size(), 5);
    assertEquals(graph.values().size(), 5);
    assertTrue(graph.contains("one"));
    assertTrue(graph.contains("one-two"));
    assertTrue(graph.contains("one-three"));
    assertFalse(graph.contains("two"));
    assertTrue(graph.contains("three"));
    assertFalse(graph.contains("four"));
    assertFalse(graph.contains("five"));
    assertTrue(graph.contains("six"));

    assertEquals(graph.getOutboundEdges("one"), asList(new BaseEdge<>("one", "one-two", "edge"), new BaseEdge<>("one", "three", "edge")));
    assertEquals(graph.getOutboundEdges("one-two"), asList(new BaseEdge<>("one-two", "one-three", "edge")));
    assertEquals(graph.getOutboundEdges("one-three"), asList(new BaseEdge<>("one-three", "six", "edge")));
    assertEquals(graph.getOutboundEdges("three"), asList());
    assertEquals(graph.getOutboundEdges("six"), asList());

    assertEquals(graph.getInboundEdges("one"), asList());
    assertEquals(graph.getInboundEdges("one-two"), asList(new BaseEdge<>("one", "one-two", "edge")));
    assertEquals(graph.getInboundEdges("one-three"), asList(new BaseEdge<>("one-two", "one-three", "edge")));
    assertEquals(graph.getInboundEdges("three"), asList(new BaseEdge<>("one", "three", "edge")));
    assertEquals(graph.getInboundEdges("six"), asList(new BaseEdge<>("one-three", "six", "edge")));
  }

  @Test
  public void traverse() {
    List<String> origins = new ArrayList<>();
    List<String> destinations = new ArrayList<>();
    graph.traverse("one", false, null, (origin, destination, edge, depth, isLast) -> {
      System.out.println("" + origin + "-(" + edge + ")->" + destination + " depth: " + depth);
      if (destination.equals("two")) {
        assertEquals(origin, "one");
        assertEquals(edge, "one-two");
        assertEquals(depth, 1);
      } else if (destination.equals("three") && origin.equals("one")) {
        assertEquals(edge, "one-three");
        assertEquals(depth, 1);
      } else if (destination.equals("three") && origin.equals("two")) {
        assertEquals(edge, "two-three");
        assertEquals(depth, 2);
      } else if (destination.equals("four")) {
        assertEquals(origin, "two");
        assertEquals(edge, "two-four");
        assertEquals(depth, 2);
      } else if (destination.equals("five")) {
        assertEquals(origin, "three");
        assertEquals(edge, "three-five");
        if (origins.contains("three")) {
          assertEquals(depth, 2);
        } else {
          assertEquals(depth, 3);
        }
      }

      origins.add(origin);
      destinations.add(destination);

      return true;
    });

    assertEquals(origins, asList("one", "two", "three", "two", "one", "three"));
    assertEquals(destinations, asList("two", "three", "five", "four", "three", "five"));
  }

  @Test
  public void traverseOnce() {
    List<String> origins = new ArrayList<>();
    List<String> destinations = new ArrayList<>();
    graph.traverse("one", true, null, (origin, destination, edge, depth, isLast) -> {
      System.out.println("" + origin + "-(" + edge + ")->" + destination + " depth: " + depth);
      if (destination.equals("two")) {
        assertEquals(origin, "one");
        assertEquals(edge, "one-two");
        assertEquals(depth, 1);
      } else if (destination.equals("three") && origin.equals("one")) {
        assertEquals(edge, "one-three");
        assertEquals(depth, 1);
      } else if (destination.equals("three") && origin.equals("two")) {
        assertEquals(edge, "two-three");
        assertEquals(depth, 2);
      } else if (destination.equals("four")) {
        assertEquals(origin, "two");
        assertEquals(edge, "two-four");
        assertEquals(depth, 2);
      } else if (destination.equals("five")) {
        assertEquals(origin, "three");
        assertEquals(edge, "three-five");
        if (origins.contains("three")) {
          assertEquals(depth, 2);
        } else {
          assertEquals(depth, 3);
        }
      }

      origins.add(origin);
      destinations.add(destination);

      return true;
    });

    assertEquals(origins, asList("one", "two", "three", "two"));
    assertEquals(destinations, asList("two", "three", "five", "four"));
  }

  /**
   * This test ensures that the traversal hits all of the child nodes at least once. We had a bug in 0.4.0 that
   * prevented the traversal from hitting everything. This ensures it is fixed.
   */
  @Test
  public void traverseMultiplePathsToTransitives() {
    HashGraph<String, String> graph = new HashGraph<>();
    graph.addEdge("one", "two", "one-two");
    graph.addEdge("two", "end1", "two-end1");
    graph.addEdge("two", "end2", "two-end2");
    graph.addEdge("two", "end3", "two-end3");
    graph.addEdge("one", "three", "one-three");
    graph.addEdge("three", "end3", "three-five");
    graph.addEdge("three", "end4", "end4");
    graph.addEdge("three", "end5", "end5");

    Set<String> visited = new HashSet<>();
    graph.traverse("one", true, null, (origin, destination, edge, depth, isLast) -> {
      visited.add(destination);
      return true;
    });

    assertEquals(visited, new HashSet<>(asList("two", "three", "end1", "end2", "end3", "end4", "end5")));
  }

  @Test
  public void traverseUp() {
    List<String> origins = new ArrayList<>();
    List<String> destinations = new ArrayList<>();
    graph.traverseUp("one", (origin, destination, edge, depth) -> {
      System.out.println("" + origin + "-(" + edge + ")->" + destination + " depth: " + depth);
      if (destination.equals("two")) {
        assertEquals(origin, "one");
        assertEquals(edge, "one-two");
        assertEquals(depth, 1);
      } else if (destination.equals("three") && origin.equals("one")) {
        assertEquals(edge, "one-three");
        assertEquals(depth, 1);
      } else if (destination.equals("three") && origin.equals("two")) {
        assertEquals(edge, "two-three");
        assertEquals(depth, 2);
      } else if (destination.equals("four")) {
        assertEquals(origin, "two");
        assertEquals(edge, "two-four");
        assertEquals(depth, 2);
      } else if (destination.equals("five")) {
        assertEquals(origin, "three");
        assertEquals(edge, "three-five");
        if (origins.contains("three")) {
          assertEquals(depth, 2);
        } else {
          assertEquals(depth, 3);
        }
      }

      origins.add(origin);
      destinations.add(destination);
    });

    assertEquals(origins, asList("three", "two", "two", "one", "three", "one"));
    assertEquals(destinations, asList("five", "three", "four", "two", "five", "three"));
  }

  @Test
  public void values() {
    assertEquals(graph.values(), new HashSet<>(asList("one", "two", "three", "four", "five")));
  }
}
