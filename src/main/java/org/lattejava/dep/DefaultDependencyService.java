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
package org.lattejava.dep;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.lattejava.dep.DependencyService.TraversalRules.GroupTraversalRule;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.Publication;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.domain.ResolvedArtifact;
import org.lattejava.dep.graph.ArtifactGraph;
import org.lattejava.dep.graph.DependencyEdgeValue;
import org.lattejava.dep.graph.DependencyGraph;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.lattejava.dep.graph.ResolvedArtifactGraph;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.dep.workflow.process.FetchResult;
import org.lattejava.dep.workflow.process.ItemSource;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.domain.Version;
import org.lattejava.output.Output;
import org.lattejava.security.MD5;
import org.lattejava.security.MD5Exception;
import org.lattejava.util.CyclicException;
import org.lattejava.util.Graph.Edge;

import static java.util.Arrays.asList;

/**
 * Default implementation of the dependency service.
 *
 * @author Brian Pontarelli
 */
public class DefaultDependencyService implements DependencyService {
  private final Output output;

  public DefaultDependencyService(Output output) {
    this.output = output;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DependencyGraph buildGraph(ReifiedArtifact project, Dependencies dependencies, Workflow workflow)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    output.debugln("Building DependencyGraph with a root of [%s]", project);
    DependencyGraph graph = new DependencyGraph(project);
    populateGraph(graph, project, dependencies, workflow, new HashSet<>(), new LinkedList<>());
    return graph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void publish(Publication publication, PublishWorkflow workflow) throws PublishException {
    if (!Files.isReadable(publication.file)) {
      throw new PublishException("The publication file [" + publication.file + "] for the publication [" + publication.artifact + "] doesn't exist.");
    }

    if (publication.sourceFile != null && !Files.isReadable(publication.sourceFile)) {
      throw new PublishException("The publication source file [" + publication.sourceFile + "] for the publication [" + publication.artifact + "] doesn't exist.");
    }

    output.infoln("Publishing [%s]", publication.artifact);

    ResolvableItem item = new ResolvableItem(publication.artifact.id.group, publication.artifact.id.project, publication.artifact.id.name,
        publication.artifact.version.toString(), publication.artifact.getArtifactMetaDataFile());
    try {
      Path amdFile = ArtifactTools.generateXML(publication.metaData);
      publishItem(item, amdFile, workflow);

      item = new ResolvableItem(item, publication.artifact.getArtifactFile());
      publishItem(item, publication.file, workflow);

      if (publication.sourceFile != null) {
        item = new ResolvableItem(item, publication.artifact.getArtifactSourceFile());
        publishItem(item, publication.sourceFile, workflow);
      } else {
        workflow.publishNegative(item, ItemSource.SAVANT);
      }
    } catch (IOException e) {
      throw new PublishException(publication, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ArtifactGraph reduce(DependencyGraph graph) throws CompatibilityException, CyclicException {
    output.debugln("Reducing DependencyGraph with a root of [%s]", graph.root);

    // Traverse graph. At each node, if the node's parents haven't all been checked. Skip it.
    // If the node's parents have all been checked, for each parent, get the version of the node for the version of the
    // parent that was kept. Ensure all these versions are compatible. Select the highest one. Add that to the
    // ArtifactGraph. Store the kept version. Continue.

    ArtifactGraph artifactGraph = new ArtifactGraph(graph.root);
    Map<ArtifactID, ReifiedArtifact> artifacts = new HashMap<>();
    artifacts.put(graph.root.id, graph.root);

    Set<Dependency> seenAtLeastOnce = new HashSet<>();

    graph.traverse(new Dependency(graph.root.id), false, null, (origin, destination, edgeValue, depth, isLast) -> {
      List<Edge<Dependency, DependencyEdgeValue>> inboundEdges = graph.getInboundEdges(destination);
      boolean alreadyCheckedAllParents = !inboundEdges.isEmpty() && inboundEdges.stream().allMatch((edge) -> artifacts.containsKey(edge.getOrigin().id));
      if (alreadyCheckedAllParents) {
        output.debugln("Already checked all parents so we know the versions of them at this point. Working on node [%s]", destination);

        // Remove from seenAtLeastOnce
        seenAtLeastOnce.remove(destination);

        return checkCompatibilityAndAddToGraph(graph, artifacts, destination, inboundEdges, artifactGraph);
      } else {
        output.debugln("Skipping dependency [%s] for now. Not all its parents have been checked", destination);
        seenAtLeastOnce.add(destination);
      }

      return true; // Always continue traversal
    });

    // Go through the seenAtLeastOnce set and determine if we should add any of the nodes to the graph
    seenAtLeastOnce.forEach((dependency) -> {
      List<Edge<Dependency, DependencyEdgeValue>> inboundEdges = graph.getInboundEdges(dependency);
      checkCompatibilityAndAddToGraph(graph, artifacts, dependency, inboundEdges, artifactGraph);
    });

    return artifactGraph;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ResolvedArtifactGraph resolve(ArtifactGraph graph, Workflow workflow, TraversalRules configuration,
                                       DependencyListener... listeners)
      throws CyclicException, ArtifactMissingException, ProcessFailureException, MD5Exception, LicenseException {
    output.debugln("Resolving ArtifactGraph with a root of [%s]", graph.root);

    ResolvedArtifact root = new ResolvedArtifact(graph.root.id, graph.root.version, graph.root.licenses, null, null);
    ResolvedArtifactGraph resolvedGraph = new ResolvedArtifactGraph(root);

    Map<ReifiedArtifact, ResolvedArtifact> map = new HashMap<>();
    map.put(graph.root, root);

    AtomicReference<GroupTraversalRule> rootTypeResolveConfiguration = new AtomicReference<>();

    graph.traverse(graph.root, false, null, (origin, destination, group, depth, isLast) -> {
      // If we are at the root, check if the group is to be resolved. If we are below the root, then we need to ensure
      // that the root was set up to fetch the group transitively
      GroupTraversalRule groupTraversalRule;
      if (origin.equals(graph.root)) {
        groupTraversalRule = configuration.rules.get(group);
        if (groupTraversalRule == null) {
          return false;
        }

        rootTypeResolveConfiguration.set(groupTraversalRule);
      } else {
        groupTraversalRule = rootTypeResolveConfiguration.get();
        if (!groupTraversalRule.transitiveGroups.isEmpty() && !groupTraversalRule.transitiveGroups.contains(group)) {
          return false;
        }
      }

      if (groupTraversalRule.disallowedLicenses.stream().anyMatch(destination.licenses::contains)) {
        throw new LicenseException(destination);
      }

      Path file = workflow.fetchArtifact(destination).toAbsolutePath();

      // Optionally fetch the source
      Path sourceFile = null;
      if (groupTraversalRule.fetchSource) {
        sourceFile = workflow.fetchSource(destination);
      }

      // Add to the graph
      ResolvedArtifact resolvedArtifact = new ResolvedArtifact(destination.id, destination.version, destination.licenses, file, sourceFile);
      resolvedGraph.addEdge(map.get(origin), resolvedArtifact, group);
      map.put(destination, resolvedArtifact);

      // Call the listeners
      asList(listeners).forEach((listener) -> listener.artifactFetched(resolvedArtifact));

      // Recurse if the configuration is set to transitive (or not set)
      return groupTraversalRule.transitive;
    });

    return resolvedGraph;
  }

  private boolean checkCompatibilityAndAddToGraph(DependencyGraph graph, Map<ArtifactID, ReifiedArtifact> artifacts,
                                                  Dependency destination, List<Edge<Dependency, DependencyEdgeValue>> inboundEdges,
                                                  ArtifactGraph artifactGraph) {
    List<Edge<Dependency, DependencyEdgeValue>> significantInbound =
        inboundEdges.stream()
                    .filter((edge) -> artifacts.containsKey(edge.getOrigin().id))
                    .filter((edge) -> edge.getValue().dependentVersion.equals(artifacts.get(edge.getOrigin().id).version))
                    .toList();

    // This is the complex part, for each inbound edge, grab the one where the origin is the correct version (based
    // on the versions we have already kept). Then for each of those, map to the dependency version (the version of
    // the destination node). Then get the min and max.
    Version min = significantInbound.stream()
                                    .map(edge -> edge.getValue().dependencyVersion)
                                    .min(Version::compareTo)
                                    .orElse(null);
    Version max = significantInbound.stream()
                                    .map(edge -> edge.getValue().dependencyVersion)
                                    .max(Version::compareTo)
                                    .orElse(null);

    output.debugln("Min [%s] and max [%s]", min, max);

    // This dependency is no longer used
    if (min == null) {
      output.debugln("NO LONGER USED");
      return false;
    }

    // Ensure min and max are compatible
    if (!destination.skipCompatibilityCheck && !min.isCompatibleWith(max)) {
      output.debugln("INCOMPATIBLE");
      throw new CompatibilityException(graph, destination, min, max);
    }

    //noinspection OptionalGetWithoutIsPresent
    DependencyEdgeValue edgeValue = significantInbound.stream()
                                                      .filter(edge -> edge.getValue().dependencyVersion.equals(max))
                                                      .findFirst()
                                                      .get()
                                                      .getValue();

    // Build the artifact for this node, save it in the Map and put it in the ArtifactGraph
    ReifiedArtifact destinationArtifact = new ReifiedArtifact(destination.id, max, edgeValue.dependencyNonSemanticVersion, edgeValue.licenses);
    artifacts.put(destination.id, destinationArtifact);

    significantInbound.forEach((edge) -> {
      ReifiedArtifact originArtifact = artifacts.get(edge.getOrigin().id);
      artifactGraph.addEdge(originArtifact, destinationArtifact, edge.getValue().type);
    });
    return true;
  }

  /**
   * Recursively populates the DependencyGraph starting with the given origin and its dependencies. This fetches the
   * ArtifactMetaData for all the dependencies and performs a breadth first traversal of the graph. If a dependency has
   * already been encountered and traversed, this does not traverse it again. The Set is used to track the dependencies
   * that have already been encountered.
   *
   * @param graph             The Graph to populate.
   * @param origin            The origin artifact that is dependent on the Dependencies given.
   * @param dependencies      The list of dependencies to extract the artifacts from.
   * @param workflow          The workflow used to fetch the AMD files.
   * @param artifactsRecursed The set of artifacts already resolved and recursed for.
   */
  private void populateGraph(DependencyGraph graph, Artifact origin, Dependencies dependencies, Workflow workflow,
                             Set<Artifact> artifactsRecursed, Deque<List<ArtifactID>> exclusions)
      throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    dependencies.groups.forEach((type, group) -> {
      output.debugln("Loading dependency group [%s]", type);

      for (Artifact dependency : group.dependencies) {
        boolean excluded = exclusions.stream()
                                     .flatMap(Collection::stream)
                                     .anyMatch(exclusion -> DependencyTools.matchesExclusion(dependency.id, exclusion));
        if (excluded) {
          output.debugln("Ignoring dependency [%s] because one of it's dependents excluded it", dependency);
          continue;
        }

        output.debugln("Loading dependency [%s] skipCompatibilityCheck=[%b]", dependency, dependency.skipCompatibilityCheck);

        ArtifactMetaData amd = workflow.fetchMetaData(dependency);

        // Create an edge using nodes so that we can be explicit
        DependencyEdgeValue edge = new DependencyEdgeValue(origin.version, origin.nonSemanticVersion, dependency.version, dependency.nonSemanticVersion, type, amd.licenses);
        graph.addEdge(new Dependency(origin.id), new Dependency(dependency.id), edge);
        if (dependency.skipCompatibilityCheck) {
          output.debugln("SKIPPING COMPATIBILITY CHECK for [%s]", dependency.id);
          graph.skipCompatibilityCheck(dependency.id);
        }

        // If we have already recursed this artifact, skip it.
        if (artifactsRecursed.contains(dependency)) {
          continue;
        }

        // Recurse
        if (amd.dependencies != null) {
          exclusions.push(dependency.exclusions);
          populateGraph(graph, dependency, amd.dependencies, workflow, artifactsRecursed, exclusions);
          exclusions.pop();
        }

        // Add the artifact to the list
        artifactsRecursed.add(dependency);
      }
    });
  }

  /**
   * Publishes a single item for the given artifact.
   *
   * @param item     The item to publish.
   * @param file     The file to publish.
   * @param workflow The publish workflow.
   * @throws IOException If the publication fails.
   */
  private void publishItem(ResolvableItem item, Path file, PublishWorkflow workflow) throws IOException {
    // Publish the MD5
    MD5 md5 = MD5.forPath(file);
    File tempFile = File.createTempFile("artifact-item", "md5");
    tempFile.deleteOnExit();
    Path md5File = tempFile.toPath();
    MD5.writeMD5(md5, md5File);
    ResolvableItem md5Item = new ResolvableItem(item, item.item + ".md5");
    workflow.publish(new FetchResult(md5File, ItemSource.SAVANT, md5Item));

    // Now publish the item itself
    workflow.publish(new FetchResult(file, ItemSource.SAVANT, item));
  }
}
