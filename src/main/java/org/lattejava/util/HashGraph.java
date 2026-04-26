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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.lattejava.util.Graph.Edge.BaseEdge;
import org.lattejava.util.Graph.EdgeFilter.IdentityEdgeFilter;

import static java.util.Arrays.asList;

/**
 * <p>
 * This class is used to construct and manage graph structures. This is a simple class that makes the navigation and
 * usage of Graphs simple and accessible.
 * </p>
 * <h3>Graphs</h3>
 * <p>
 * Graphs are simple structures that model nodes with any number of connections between nodes. The connections are
 * bi-directional and are called Edges. A two node graph with an edge between the nodes looks like this:
 * </p>
 * <pre>
 * node1 &lt;---&gt; node2
 * </pre>
 * <p>
 * The important point about Graphs is that they don't enforce a top level node that controls the entire structure like
 * trees do. Instead, the graph has access to all nodes and the connections between them. This makes finding a Node easy
 * and then traversing the graph also easy.
 * </p>
 * <h3>Generics</h3>
 * <p>
 * There are two generics for a Graph. The first variable T is the content of the nodes themselves. Each node can stored
 * a single value. The second generic is the value that can be associated with the Edge between nodes. This is carried
 * throughout the entire graph structure making it very strongly typed.
 * </p>
 * <h3>Internals</h3>
 * <p>
 * It is important to understand how the Graph works internally. Nodes are stored in a Map whose key is the value for
 * the node. If the graph is storing Strings then only a single node can exist with the value <em>foo</em>. This means
 * that the graph does not allow duplicates. Therefore it would be impossible to have two nodes whose values are
 * <em>foo</em> with different edges. The key of the Map is a {@link HashNode} object. The node stores the value as
 * well
 * as all the edges.
 * </p>
 * <h3>Node values</h3>
 * <p>
 * Due to the implementation of the graph, all values must have a good equal and hashcode implementation. Using the
 * object identity is allowed and will then manage the graph based on the heap location of the value objects (pointers
 * are used for the java.lang.Object version of equals and hashcode).
 * </p>
 * <h3>Thread safety</h3>
 * <p>
 * The Graph is not thread safe. Classes must synchronize on the graph instance in order to protect multi-threaded use.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class HashGraph<T, U> implements Graph<T, U> {
  private final Map<T, HashNode<T, U>> nodes = new LinkedHashMap<>();

  @Override
  public void addEdge(T origin, T destination, U value) {
    HashNode<T, U> originNode = addNode(origin);
    HashNode<T, U> destinationNode = addNode(destination);

    originNode.addOutboundEdge(destinationNode, value);
    destinationNode.addInboundEdge(originNode, value);
  }

  @Override
  public boolean contains(T value) {
    return nodes.containsKey(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final HashGraph hashGraph = (HashGraph) o;
    return nodes.equals(hashGraph.nodes);
  }

  /**
   * Finds the first node in the graph that satisfies the predicate using a depth first traversal of the graph.
   *
   * @param rootValue The value of the node to start the traversal from.
   * @param predicate The predicate used to find the node.
   * @return The value of the first node that matches the predicate starting at the rootValue node. Null if the
   *     rootValue not doesn't exist or if no nodes match the predicate.
   * @throws CyclicException If there is a cycle in the graph.
   */
  @Override
  public T find(T rootValue, Predicate<T> predicate) throws CyclicException {
    HashNode<T, U> rootNode = nodes.get(rootValue);
    if (rootNode == null) {
      return null;
    }

    Set<T> visited = new HashSet<>();
    return find(rootNode, visited, predicate);
  }

  @Override
  public List<Edge<T, U>> getInboundEdges(T value) {
    HashNode<T, U> node = nodes.get(value);
    if (node == null) {
      return null;
    }

    return node.inbound
        .stream()
        .map(HashEdge::toEdge)
        .collect(Collectors.toList());
  }

  @Override
  public List<Edge<T, U>> getOutboundEdges(T value) {
    HashNode<T, U> node = nodes.get(value);
    if (node == null) {
      return null;
    }

    return node.outbound
        .stream()
        .map(HashEdge::toEdge)
        .collect(Collectors.toList());
  }

  @Override
  public List<Path<T>> getPaths(T origin, T destination) {
    List<Path<T>> paths = new ArrayList<>();
    LinkedList<T> current = new LinkedList<>();
    traverse(origin, false, null, (originValue, destinationValue, edgeValue, depth, isLast) -> {
      if (depth == 1) {
        current.clear();
        current.add(origin);
      }

      current.add(destinationValue);

      boolean finished = destinationValue.equals(destination);
      if (finished) {
        paths.add(new BasePath<>(current));
      }

      return !finished;
    });

    return paths;
  }

  @Override
  public int hashCode() {
    return nodes.hashCode();
  }

  @Override
  @SuppressWarnings("unchecked")
  public void prune(T... excludes) {
    Set<T> excludeValues = new HashSet<>(asList(excludes));
    nodes.values().forEach((node) -> {
      if (!excludeValues.contains(node.value) && node.inbound.isEmpty()) {
        removeNode(node.value);
      }
    });
  }

  @Override
  public void removeEdge(T origin, T destination, U value) {
    HashNode<T, U> originNode = nodes.get(origin);
    HashNode<T, U> destinationNode = nodes.get(destination);

    HashEdge<T, U> edge = new HashEdge<>(originNode, destinationNode, value);
    originNode.removeEdge(edge);
    destinationNode.removeEdge(edge);
  }

  @Override
  public void removeNode(T value) throws CyclicException {
    HashNode<T, U> node = nodes.get(value);
    if (node == null) {
      return;
    }

    // Get the outbound edges first and then remove this nodes outbound and inbound edges
    List<HashEdge<T, U>> outboundEdges = new ArrayList<>(node.outbound);
    clearEdges(node);

    outboundEdges.stream()
                 .filter((edge) -> edge.destination.inbound.isEmpty())
                 .forEach((edge) -> removeNode(edge.destination.value));

    nodes.remove(value);
  }

  @Override
  public int size() {
    return nodes.size();
  }

  /**
   * Performs a depth first traversal of the graph. For each node, the GraphConsumer is called. The traversal WILL
   * traverse the same node twice if it has multiple connections.
   *
   * @param rootValue      The value of the node to start the traversal from.
   * @param visitNodesOnce Determines if nodes are visited once if they have multiple links.
   * @param edgeFilter     The EdgeFilter that is used to control the traversal.
   * @param consumer       The GraphConsumer that is called for each edge.
   * @throws CyclicException If there is a cycle in the graph.
   */
  @Override
  public void traverse(T rootValue, boolean visitNodesOnce, EdgeFilter<T, U> edgeFilter, GraphConsumer<T, U> consumer) throws CyclicException {
    HashNode<T, U> rootNode = nodes.get(rootValue);
    if (rootNode == null) {
      throw new IllegalArgumentException("Invalid rootValue [" + rootValue + "] to start the traversal from.");
    }

    if (edgeFilter == null) {
      edgeFilter = new IdentityEdgeFilter<>();
    }

    Set<T> cycleCheck = new HashSet<>();
    Set<T> visited = new HashSet<>();
    traverse(rootNode, null, visitNodesOnce, cycleCheck, visited, edgeFilter, consumer, 1);
  }

  /**
   * Performs a depth first traversal of the graph. For each node, the GraphConsumer is called. The traversal WILL
   * traverse the same node twice if it has multiple connections.
   *
   * @param rootValue The value of the node to start the traversal from.
   * @param visitor   The GraphVisitor that is called for each edge.
   * @throws CyclicException If there is a cycle in the graph.
   */
  @Override
  public void traverseUp(T rootValue, GraphVisitor<T, U> visitor) throws CyclicException {
    HashNode<T, U> rootNode = nodes.get(rootValue);
    if (rootNode == null) {
      throw new IllegalArgumentException("Invalid rootValue [" + rootValue + "] to start the traversal from.");
    }

    Set<T> visited = new HashSet<>();
    traverseUp(rootNode, visited, visitor, 1);
  }

  /**
   * Returns a Set that contains all of the unique artifacts contained in the graph.
   *
   * @return All the artifacts.
   */
  @Override
  public Set<T> values() {
    return new HashSet<>(nodes.keySet());
  }

  protected HashNode<T, U> addNode(T value) {
    HashNode<T, U> node = nodes.get(value);
    if (node == null) {
      node = new HashNode<>(value);
      nodes.put(value, node);
    }

    return node;
  }

  protected void clearEdges(HashNode<T, U> node) {
    // Prevent concurrent modification exceptions by using a new ArrayList
    new ArrayList<>(node.outbound).forEach((edge) -> removeEdge(edge.origin.value, edge.destination.value, edge.value));
    node.outbound.clear();

    // Prevent concurrent modification exceptions by using a new ArrayList
    new ArrayList<>(node.inbound).forEach((edge) -> removeEdge(edge.origin.value, edge.destination.value, edge.value));
    node.inbound.clear();
  }

  protected T find(HashNode<T, U> root, Set<T> visited, Predicate<T> predicate) {
    if (predicate.test(root.value)) {
      return root.value;
    }

    for (HashEdge<T, U> edge : root.outbound) {
      if (visited.contains(edge.destination.value)) {
        throw new CyclicException("Encountered the graph node [" + edge.destination.value + "] twice. Your graph has a cycle");
      }

      visited.add(root.value);
      T result = find(edge.destination, visited, predicate);
      if (result != null) {
        return result;
      }
      visited.remove(root.value);
    }

    return null;
  }

  protected HashNode<T, U> getNode(T value) {
    return nodes.get(value);
  }

  protected void traverse(HashNode<T, U> root, HashEdge<T, U> traversedEdge, boolean visitNodesOnce, Set<T> cycleCheck,
                          Set<T> visited, EdgeFilter<T, U> edgeFilter, GraphConsumer<T, U> consumer, int depth) {
    List<HashEdge<T, U>> edges = root.outbound;
    if (traversedEdge != null) {
      edges = root.outbound
          .stream()
          .filter((edge) -> edgeFilter.filter(edge.toEdge(), traversedEdge.toEdge()))
          .collect(Collectors.toList());
    }

    for (int i = 0; i < edges.size(); i++) {
      HashEdge<T, U> edge = edges.get(i);
      if (cycleCheck.contains(edge.destination.value)) {
        throw new CyclicException("Encountered the graph node [" + edge.destination.value + "] twice. Your graph has a cycle");
      }

      if (visitNodesOnce && visited.contains(edge.destination.value)) {
        continue;
      }

      cycleCheck.add(root.value);

      boolean cont = consumer.consume(root.value, edge.destination.value, edge.value, depth, i + 1 == edges.size());
      visited.add(edge.destination.value);

      if (cont) {
        traverse(edge.destination, edge, visitNodesOnce, cycleCheck, visited, edgeFilter, consumer, depth + 1);
      }

      cycleCheck.remove(root.value);
    }
  }

  protected void traverseUp(HashNode<T, U> root, Set<T> visited, GraphVisitor<T, U> consumer, int depth) {
    root.outbound.forEach((edge) -> {
      if (visited.contains(edge.destination.value)) {
        throw new CyclicException("Encountered the graph node [" + edge.destination.value + "] twice. Your graph has a cycle");
      }

      visited.add(root.value);
      traverseUp(edge.destination, visited, consumer, depth + 1);
      consumer.visit(root.value, edge.destination.value, edge.value, depth);
      visited.remove(root.value);
    });
  }

  /**
   * This class is the edge between nodes in the graph.
   *
   * @author Brian Pontarelli
   */
  protected static class HashEdge<T, U> {
    public final HashNode<T, U> destination;

    public final HashNode<T, U> origin;

    public final U value;

    public HashEdge(HashNode<T, U> origin, HashNode<T, U> destination, U value) {
      this.origin = origin;
      this.destination = destination;
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final HashEdge hashEdge = (HashEdge) o;
      return destination.value.equals(hashEdge.destination.value) && origin.value.equals(hashEdge.origin.value) && value.equals(hashEdge.value);
    }

    @Override
    public int hashCode() {
      int result = destination.value.hashCode();
      result = 31 * result + origin.value.hashCode();
      result = 31 * result + value.hashCode();
      return result;
    }

    public Edge<T, U> toEdge() {
      return new BaseEdge<>(origin.value, destination.value, value);
    }
  }

  /**
   * This class is a single node in the HashGraph.
   *
   * @author Brian Pontarelli
   */
  protected static class HashNode<T, U> {
    public final List<HashEdge<T, U>> inbound = new ArrayList<>();

    public final List<HashEdge<T, U>> outbound = new ArrayList<>();

    public T value;

    public HashNode(T value) {
      this.value = value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final HashNode<T, U> that = (HashNode<T, U>) o;
      if (!value.equals(that.value)) {
        return false;
      }

      // Compare the lists brute force (to avoid nasty Comparable infection)
      if (inbound.size() != that.inbound.size()) {
        return false;
      }
      if (outbound.size() != that.outbound.size()) {
        return false;
      }
      for (HashEdge<T, U> myEdge : outbound) {
        boolean matches = that.outbound.stream().anyMatch(
            (HashEdge<T, U> theirEdge) -> myEdge.value.equals(theirEdge.value) && myEdge.destination.value.equals(theirEdge.destination.value)
        );
        if (!matches) {
          return false;
        }
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = inbound.hashCode();
      result = 31 * result + outbound.hashCode();
      result = 31 * result + value.hashCode();
      return result;
    }

    public void removeEdge(HashEdge<T, U> edge) {
      outbound.remove(edge);
      inbound.remove(edge);
    }

    /**
     * @return The toString of the node's value.
     */
    public String toString() {
      return value.toString();
    }

    void addInboundEdge(HashNode<T, U> origin, U edgeValue) {
      HashEdge<T, U> edge = new HashEdge<>(origin, this, edgeValue);
      if (!inbound.contains(edge)) {
        inbound.add(edge);
      }
    }

    void addOutboundEdge(HashNode<T, U> destination, U edgeValue) {
      HashEdge<T, U> edge = new HashEdge<>(this, destination, edgeValue);
      if (!outbound.contains(edge)) {
        outbound.add(edge);
      }
    }
  }
}
