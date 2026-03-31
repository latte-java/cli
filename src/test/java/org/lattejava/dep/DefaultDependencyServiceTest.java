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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.DependencyService.TraversalRules;
import org.lattejava.dep.DependencyService.TraversalRules.GroupTraversalRule;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.Publication;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.domain.ResolvedArtifact;
import org.lattejava.dep.graph.ArtifactGraph;
import org.lattejava.dep.graph.DependencyEdgeValue;
import org.lattejava.dep.graph.DependencyGraph;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.lattejava.dep.graph.ResolvedArtifactGraph;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.FetchWorkflow;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.dep.workflow.process.CacheProcess;
import org.lattejava.dep.workflow.process.MavenProcess;
import org.lattejava.dep.workflow.process.URLProcess;
import org.lattejava.domain.Version;
import org.lattejava.security.MD5;
import org.lattejava.security.MD5Exception;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the default dependency service.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class DefaultDependencyServiceTest extends BaseUnitTest {
  public Dependencies dependencies;

  public ReifiedArtifact exclusions = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "exclusions", "exclusions", "jar"), new Version("1.0.0"), License.Licenses.get("ApacheV2_0"));

  public DependencyGraph goodGraph;

  public ArtifactGraph goodReducedGraph;

  public ReifiedArtifact integrationBuild = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "integration-build", "integration-build", "jar"), new Version("2.1.1-{integration}"), License.Licenses.get("ApacheV2_0"));

  public ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"), License.Licenses.get("ApacheV2_0"));

  public ReifiedArtifact leaf1 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf", "leaf1", "jar"), new Version("1.0.0"), License.Licenses.get("GPLV2_0"));

  public ReifiedArtifact leaf1_1 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf1", "leaf1", "jar"), new Version("1.0.0"), License.parse("Commercial", "Commercial license"));

  public ReifiedArtifact leaf2 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf", "leaf2", "jar"), new Version("1.0.0"), License.Licenses.get("LGPLV2_1"));

  public ReifiedArtifact leaf2_2 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf2", "leaf2", "jar"), new Version("1.0.0"), License.parse("OtherNonDistributableOpenSource", "Open source"));

  public ReifiedArtifact leaf3_3 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf3", "leaf3", "jar"), new Version("1.0.0"), License.Licenses.get("ApacheV2_0"));

  public ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"), License.Licenses.get("ApacheV2_0"));

  public ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"), License.Licenses.get("ApacheV2_0"));

  public ReifiedArtifact project = new ReifiedArtifact("org.lattejava.test:project:1.0.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")));

  public ResolvedArtifact projectResolved = new ResolvedArtifact(project.id, project.version, Collections.singletonList(License.Licenses.get("ApacheV2_0")), null, null);

  public ResolvedArtifact resolvedIntegrationBuild;

  public ResolvedArtifact resolvedIntermediate;

  public ResolvedArtifact resolvedLeaf1;

  public ResolvedArtifact resolvedLeaf1_1;

  public ResolvedArtifact resolvedLeaf2_2;

  public ResolvedArtifact resolvedLeaf3_3;

  public ResolvedArtifact resolvedMultipleVersions;

  public ResolvedArtifact resolvedMultipleVersionsDifferentDeps;

  public DefaultDependencyService service = new DefaultDependencyService(output);

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf1
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf1
   *              |              ^           (1.0.0)-->(2.1.1-{integration})integration-build
   *              |              |           (1.1.0)-->(2.1.1-{integration})integration-build
   *              |              |
   *              |->(1.0.0)intermediate
   *              |              |
   *              |             \/
   *              |          (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies(1.0.0)-->(1.0.0)leaf:leaf2
   *              |                                                 (1.0.0,1.1.0)-->(1.0.0)leaf1:leaf1
   *              |                                                 (1.1.0)-->(1.0.0)leaf2:leaf2
   *              |                                                 (1.1.0)-->(1.0.0)leaf3:leaf3 (optional)
   * </pre>
   */
  @BeforeMethod
  public void beforeMethod() throws IOException {
    goodGraph = new DependencyGraph(project);
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV1_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("LGPLV2_1")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2_2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("OtherNonDistributableOpenSource", "Open source")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf3_3.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));

    goodReducedGraph = new ArtifactGraph(project);
    goodReducedGraph.addEdge(project, multipleVersions, "compile");
    goodReducedGraph.addEdge(project, intermediate, "runtime");
    goodReducedGraph.addEdge(project, multipleVersionsDifferentDeps, "compile");
    goodReducedGraph.addEdge(intermediate, multipleVersions, "compile");
    goodReducedGraph.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    goodReducedGraph.addEdge(multipleVersions, leaf1, "compile");
    goodReducedGraph.addEdge(multipleVersions, integrationBuild, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");
    goodReducedGraph.addEdge(multipleVersionsDifferentDeps, leaf3_3, "runtime");

    // Used for build and publish tests
    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(multipleVersions.id, new Version("1.0.0")),
            new Artifact(multipleVersionsDifferentDeps.id, new Version("1.0.0"))
        ),
        new DependencyGroup("runtime", true,
            new Artifact(intermediate.id, new Version("1.0.0"))
        )
    );

    resolvedIntermediate = new ResolvedArtifact("org.lattejava.test:intermediate:1.0.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")), cache.resolve("org/savantbuild/test/intermediate/1.0.0/intermediate-1.0.0.jar").toAbsolutePath(), null);
    resolvedMultipleVersions = new ResolvedArtifact("org.lattejava.test:multiple-versions:1.1.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")), cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar").toAbsolutePath(), null);
    resolvedMultipleVersionsDifferentDeps = new ResolvedArtifact("org.lattejava.test:multiple-versions-different-dependencies:1.1.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")), cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar").toAbsolutePath(), null);
    resolvedLeaf1 = new ResolvedArtifact("org.lattejava.test:leaf:leaf1:1.0.0:jar", Collections.singletonList(License.Licenses.get("GPLV2_0")), cache.resolve("org/savantbuild/test/leaf/1.0.0/leaf1-1.0.0.jar").toAbsolutePath(), null);
    resolvedLeaf1_1 = new ResolvedArtifact("org.lattejava.test:leaf1:1.0.0", Collections.singletonList(License.parse("Commercial", "Commercial license")), cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar").toAbsolutePath(), null);
    resolvedLeaf2_2 = new ResolvedArtifact("org.lattejava.test:leaf2:1.0.0", Collections.singletonList(License.parse("OtherNonDistributableOpenSource", "Open source")), cache.resolve("org/savantbuild/test/leaf2/1.0.0/leaf2-1.0.0.jar").toAbsolutePath(), null);
    resolvedLeaf3_3 = new ResolvedArtifact("org.lattejava.test:leaf3:1.0.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")), cache.resolve("org/savantbuild/test/leaf3/1.0.0/leaf3-1.0.0.jar").toAbsolutePath(), null);
    resolvedIntegrationBuild = new ResolvedArtifact("org.lattejava.test:integration-build:2.1.1-{integration}", Collections.singletonList(License.Licenses.get("ApacheV2_0")), integration.resolve("org/savantbuild/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar").toAbsolutePath(), null);

    PathTools.prune(cache);
    PathTools.prune(mavenCache);
    assertFalse(Files.isDirectory(cache));
    assertFalse(Files.isDirectory(mavenCache));

    // Reset workflow to the default (some tests override it and it's static)
    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), integration.toString(), null),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), integration.toString(), null)
        ),
        output
    );
  }

  @Test
  public void buildGraph() {
    DependencyGraph actual = service.buildGraph(project, dependencies, workflow);
    assertEquals(actual, goodGraph);
  }

  @Test
  public void buildGraphFailureBadAMDMD5() {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.lattejava.test:bad-amd-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when fetching item from [http://localhost:7042/test-deps/savant/org/savantbuild/test/bad-amd-md5/1.0.0/bad-amd-md5-1.0.0.jar.amd]");
    }
  }

  @Test
  public void buildGraphFailureMissingAMD() {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.lattejava.test:missing-amd:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.lattejava.test:missing-amd:1.0.0"));
    }
  }

  @Test
  public void buildGraphFailureMissingDependency() {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.lattejava.test:missing:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.lattejava.test:missing:1.0.0"));
    }
  }

  @Test
  public void buildGraphFailureMissingMD5() {
    try {
      Dependencies dependencies = makeSimpleDependencies("org.lattejava.test:missing-md5:1.0.0");
      service.buildGraph(project, dependencies, workflow);
      fail("Should have failed");
    } catch (ArtifactMetaDataMissingException e) {
      assertEquals(e.artifactMissingAMD, new Artifact("org.lattejava.test:missing-md5:1.0.0"));
    }
  }

  @Test
  public void exclusions() {
    // Override to add exclusions. Because the project AND the exclusions artifact both exclude leaf1 and leaf1_1, this prevents them from being included in the graph
    goodGraph = new DependencyGraph(project);
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV1_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(exclusions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(exclusions.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(exclusions.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("LGPLV2_1")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2_2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("OtherNonDistributableOpenSource", "Open source")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf3_3.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    // Excluded
    //   * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(leaf1.id, leaf1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    //   * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(leaf1.id, leaf1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    //   * goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id, multipleVersionsDifferentDeps.nonSemanticVersion), new Dependency(leaf1_1.id, leaf1_1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(multipleVersions.id, new Version("1.0.0"), Collections.singletonList(leaf1.id)),
            new Artifact(multipleVersionsDifferentDeps.id, new Version("1.0.0"))
        ),
        new DependencyGroup("runtime", true,
            new Artifact(exclusions.id, new Version("1.0.0"))
        )
    );

    var result = buildAndResolve();
    assertEquals(result.dependencyGraph, goodGraph);
  }

  @Test
  public void mavenCentralCircularDependencyButOptional() {
//    output.enableDebug();

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.apache.groovy:groovy:4.0.6")
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mavenCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mavenCache.toString())
        ),
        output
    );

    buildAndResolve();
  }

  @Test
  public void mavenCentralComplex() {
//    output.enableDebug();

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("io.vertx:vertx-core:3.9.8")
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mavenCache.toString())
        ),
        output
    );
    workflow.mappings.put("io.netty:netty-all:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-dns:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-socks:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-dev-tools:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-jni-util:0.0.3.Final", new Version("0.0.3"));
    workflow.mappings.put("io.netty:netty-parent:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-tcnative:2.0.39.Final", new Version("2.0.39"));
    workflow.mappings.put("io.netty:netty-tcnative-boringssl-static:2.0.39.Final", new Version("2.0.39"));
    workflow.mappings.put("io.netty:netty-tcnative-parent:2.0.39.Final", new Version("2.0.39"));
    workflow.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-epoll:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-kqueue:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-unix-common:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.projectreactor.tools:blockhound:1.0.6.RELEASE", new Version("1.0.6"));
    workflow.mappings.put("org.eclipse.jetty.alpn:alpn-api:1.1.2.v20150522", new Version("1.1.2+v20150522"));
    workflow.mappings.put("org.eclipse.jetty.npn:npn-api:1.1.1.v20141010", new Version("1.1.1+v20141010"));
    workflow.mappings.put("org.eclipse.tycho:org.eclipse.osgi:3.13.0.v20180226-1711", new Version("3.13.0+v20180226-1711"));
    workflow.mappings.put("org.jboss.marshalling:jboss-marshalling:1.4.11.Final", new Version("1.4.11"));
    workflow.mappings.put("org.jboss.marshalling:jboss-marshalling-parent:1.4.11.Final", new Version("1.4.11"));
    workflow.mappings.put("org.jboss.spec.javax.jms:jboss-jms-api_1.1_spec:1.0.1.Final", new Version("1.0.1"));
    workflow.mappings.put("org.mvel:mvel2:2.3.1.Final", new Version("2.3.1"));
    workflow.mappings.put("org.springframework:spring-beans:3.0.5.RELEASE", new Version("3.0.5"));
    workflow.mappings.put("org.xerial.snappy:snappy-java:1.1.7.1", new Version("1.1.7+1"));

    buildAndResolve();
  }

  @Test
  public void nonSemanticVersions() {
//    output.enableDebug();

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:has-non-semantic-versioned-dep:1.0.0")
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "http://localhost:7042/test-deps/maven", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mavenCache.toString())
        ),
        output
    );
    workflow.mappings.put("org.lattejava.test:badver:1.0.0.Final", new Version("1.0.0"));

    ArtifactID hasNonSemanticId = new ArtifactID("org.lattejava.test:has-non-semantic-versioned-dep");
    ArtifactID badVerId = new ArtifactID("org.lattejava.test:badver");
    DependencyGraph expected = new DependencyGraph(project);
    expected.addEdge(new Dependency(project.id), new Dependency(hasNonSemanticId), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("GPLV2_0", null)));
    expected.addEdge(new Dependency(hasNonSemanticId), new Dependency(badVerId), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Apache-2.0", null)));
    expected.addEdge(new Dependency(badVerId), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));

    var result = buildAndResolve();
    assertEquals(result.dependencyGraph, expected);

    ReifiedArtifact badVer = result.artifactGraph.values()
                                                 .stream()
                                                 .filter(r -> r.id.name.equals("badver"))
                                                 .findFirst()
                                                 .orElseThrow();
    assertEquals(badVer.nonSemanticVersion, "1.0.0.Final");
  }

  @Test
  public void nonSemanticVersions_publishRouting() {
    // Resolves the same mixed Savant + Maven dependency tree as nonSemanticVersions,
    // then verifies publish routing: Maven-sourced items only in maven cache,
    // Savant-sourced items only in savant cache, no re-published semantic-version copies.

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:has-non-semantic-versioned-dep:1.0.0")
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), integration.toString(), mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "http://localhost:7042/test-deps/maven", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), integration.toString(), mavenCache.toString())
        ),
        output
    );
    workflow.mappings.put("org.lattejava.test:badver:1.0.0.Final", new Version("1.0.0"));

    buildAndResolve();

    // Maven-sourced "badver" JAR and POM should be in maven cache only
    assertTrue(Files.isRegularFile(mavenCache.resolve("org/savantbuild/test/badver/1.0.0.Final/badver-1.0.0.Final.jar")));
    assertTrue(Files.isRegularFile(mavenCache.resolve("org/savantbuild/test/badver/1.0.0.Final/badver-1.0.0.Final.pom")));
    // No re-published copy at the semantic version path
    assertFalse(Files.exists(mavenCache.resolve("org/savantbuild/test/badver/1.0.0/badver-1.0.0.jar")));
    assertFalse(Files.exists(cache.resolve("org/savantbuild/test/badver/1.0.0/badver-1.0.0.jar")));
    // Maven-sourced items should NOT be in savant cache
    assertFalse(Files.exists(cache.resolve("org/savantbuild/test/badver/1.0.0.Final/badver-1.0.0.Final.jar")));
    assertFalse(Files.exists(cache.resolve("org/savantbuild/test/badver/1.0.0.Final/badver-1.0.0.Final.pom")));

    // Savant-sourced "has-non-semantic-versioned-dep" should be in savant cache only
    assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/has-non-semantic-versioned-dep/1.0.0/has-non-semantic-versioned-dep-1.0.0.jar")));
    assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/has-non-semantic-versioned-dep/1.0.0/has-non-semantic-versioned-dep-1.0.0.jar.amd")));
    assertFalse(Files.exists(mavenCache.resolve("org/savantbuild/test/has-non-semantic-versioned-dep/1.0.0/has-non-semantic-versioned-dep-1.0.0.jar")));

    // Savant-sourced "leaf1" should be in savant cache only
    assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar")));
    assertFalse(Files.exists(mavenCache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar")));

    // No AMD files generated for Maven-sourced "badver"
    assertFalse(Files.exists(cache.resolve("org/savantbuild/test/badver/1.0.0/badver-1.0.0.jar.amd")));
    assertFalse(Files.exists(mavenCache.resolve("org/savantbuild/test/badver/1.0.0.Final/badver-1.0.0.Final.jar.amd")));
  }

  @Test
  public void publishRouting_savantOnly() {
    // Resolves a Savant-only dependency tree and verifies all items go to savant cache,
    // nothing goes to maven cache.

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:leaf1:1.0.0")
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), integration.toString(), mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "http://localhost:7042/test-deps/maven", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), integration.toString(), mavenCache.toString())
        ),
        output
    );

    buildAndResolve();

    // Savant-sourced items in savant cache
    assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar")));
    assertTrue(Files.isRegularFile(cache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar.amd")));
    // Nothing in maven cache
    assertFalse(Files.exists(mavenCache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar")));
    assertFalse(Files.exists(mavenCache.resolve("org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar.amd")));
  }

  @Test
  public void publishMissingFile() {
    Artifact artifact = new Artifact("org.lattejava.test:publication-with-source:1.0.0");
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, License.Licenses.get("BSD_2_Clause"));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("MissingFile.txt"), null);
    Path cache = projectDir.resolve("build/test/publish");
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, cache.toString(), null, null));
    try {
      service.publish(publication, workflow);
    } catch (PublishException e) {
      assertTrue(e.getMessage().contains("The publication file"));
    }
  }

  @Test
  public void publishMissingSourceFile() {
    Artifact artifact = new Artifact("org.lattejava.test:publication-with-source:1.0.0");
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, License.Licenses.get("BSD_2_Clause"));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), Paths.get("MissingFile.txt"));
    Path cache = projectDir.resolve("build/test/publish");
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, cache.toString(), null, null));
    try {
      service.publish(publication, workflow);
    } catch (PublishException e) {
      assertTrue(e.getMessage().contains("The publication source file"));
    }
  }

  @Test
  public void publishNonSemanticVersion() throws Exception {
    Path cache = projectDir.resolve("build/test/publish");
    PathTools.prune(cache);

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.badver:badver:1.0.0", "1.0.0.Borked", false, Collections.emptyList())
        )
    );

    Artifact artifact = new Artifact("org.lattejava.test:publication-with-source:1.0.0");
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, License.Licenses.get("BSD_2_Clause"));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"));
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, cache.toString(), null, null));
    service.publish(publication, workflow);

    Path amdFile = projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd");
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd.md5")));
    assertTrue(Files.isRegularFile(amdFile));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar")));

    // Ensure the MD5 files are correct (these methods throw exceptions if they aren't
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar.md5"));

    Map<String, Version> mappings = new HashMap<>();
    mappings.put("org.badver:badver:1.0.0.Borked", new Version("1.0.0"));

    ArtifactMetaData actual = ArtifactTools.parseArtifactMetaData(amdFile, mappings);
    ArtifactMetaData expected = new ArtifactMetaData(dependencies, License.Licenses.get("BSD_2_Clause"));
    assertEquals(actual, expected);
  }

  @Test
  public void publishWithSource() throws IOException {
    Path cache = projectDir.resolve("build/test/publish");
    PathTools.prune(cache);

    Artifact artifact = new Artifact("org.lattejava.test:publication-with-source:1.0.0");
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, License.Licenses.get("BSD_2_Clause"));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"));
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, cache.toString(), null, null));
    service.publish(publication, workflow);

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar")));

    // Ensure the MD5 files are correct (these methods throw exceptions if they aren't
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.amd.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0.jar.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-with-source/1.0.0/publication-with-source-1.0.0-src.jar.md5"));
  }

  @Test
  public void publishWithoutSource() throws IOException {
    Path cache = projectDir.resolve("build/test/publish");
    PathTools.prune(cache);

    Artifact artifact = new Artifact("org.lattejava.test:publication-without-source:1.0.0");
    ArtifactMetaData amd = new ArtifactMetaData(dependencies, License.Licenses.get("BSD_2_Clause"));
    Publication publication = new Publication(artifact, amd, projectDir.resolve("src/test/java/org/savantbuild/dep/TestFile.txt"), null);
    PublishWorkflow workflow = new PublishWorkflow(new CacheProcess(output, cache.toString(), null, null));
    service.publish(publication, workflow);

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.amd.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.amd")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.md5")));
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar")));
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0-src.jar.md5")));
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0-src.jar")));

    // Ensure the MD5 files are correct (these methods throw exceptions if they aren't
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.amd.md5"));
    MD5.load(projectDir.resolve("build/test/publish/org/savantbuild/test/publication-without-source/1.0.0/publication-without-source-1.0.0.jar.md5"));
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf1
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf1
   *              |              ^           (1.0.0)-->(2.1.1-{integration})integration-build
   *              |              |           (1.1.0)-->(2.1.1-{integration})integration-build
   *              |           (1.0.0)
   *              |->(1.0.0)intermediate
   *              |           (1.0.0)
   *              |              |
   *              |             \/
   *              |          (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies(1.0.0)-->(1.0.0)leaf:leaf2
   *              |                                                 (1.0.0)-->(1.0.0)leaf1:leaf1
   *              |                                                 (1.1.0)-->(2.0.0)leaf1:leaf1 // This is the upgrade
   *              |                                                 (1.1.0)-->(1.0.0)leaf2:leaf2
   *              |                                                 (1.1.0)-->(1.0.0)leaf3:leaf3 (optional)
   * </pre>
   * <p>
   * Notice that the leaf1:leaf1 node gets upgrade across a major version. This is allowed because the
   * multiple-versions-different-dependencies node gets upgrade to 1.1.0 and therefore all the dependencies below it are
   * from the 1.1.0 version.
   */
  @Test
  public void reduceComplex() {
    ReifiedArtifact leaf1 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf", "leaf1", "jar"), new Version("1.0.0"));
    ReifiedArtifact leaf2 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf", "leaf2", "jar"), new Version("1.0.0"));
    ReifiedArtifact leaf1_1 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf1", "leaf1", "jar"), new Version("2.0.0"));
    ReifiedArtifact leaf2_2 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf2", "leaf2", "jar"), new Version("1.0.0"));
    ReifiedArtifact leaf3_3 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf3", "leaf3", "jar"), new Version("1.0.0"));
    ReifiedArtifact integrationBuild = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "integration-build", "integration-build", "jar"), new Version("2.1.1-{integration}"));
    ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"));
    ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"));
    ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile"));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2_2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf3_3.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime"));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    expected.addEdge(multipleVersions, leaf1, "compile");
    expected.addEdge(multipleVersions, integrationBuild, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf1_1, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf2_2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf3_3, "runtime");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(2.0.0)leaf:leaf
   *              |              ^                         (1.0.0) (2.0.0)
   *              |              |                           |       ^
   *              |->(1.0.0)intermediate                     |       |
   *              |              |                           |       |
   *              |             \/                           |       |
   *              |          (1.1.0)                      (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will upgrade leaf to 2.0.0, it should ignore the 1.0.0 version of it and not generate an
   * error.
   */
  @Test
  public void reduceComplexCross() {
    ReifiedArtifact leaf = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf", "leaf", "jar"), new Version("2.0.0"));
    ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"));
    ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"));
    ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile"));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "runtime"));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    expected.addEdge(multipleVersions, leaf, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf, "runtime");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)intermediate2:intermediate2(2.0.0)-->(2.0.0)leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)intermediate2:intermediate2                (1.0.0)
   *              |              ^                                  (2.0.0) (1.0.0)                        ^
   *              |              |                                    ^      ^                             |
   *              |->(1.0.0)intermediate                    ----------|      |                             |
   *              |              |                     -----|  --------------                              |
   *              |             \/                    |       |                                            |
   *              |          (1.1.0)               (1.0.0) (1.1.0)                                         |
   *              |-->(1.0.0)multiple-versions-different-dependencies(1.1.0)-------------------------------|
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the intermediate2 has two versions, 1.0.0 and 2.0.0. However, multiple-versions-different-dependencies
   * gets upgraded to 1.1.0, which means that intermediate2 gets downgraded to 1.0.0. This also means that leaf should
   * be downgraded to 1.0.0 since the dependency from intermediate2(2.0.0) should be ignored since that version is never
   * used in the graph.
   */
  @Test
  public void reduceDowngrade() {
    ReifiedArtifact leaf = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "leaf", "leaf", "jar"), new Version("1.0.0"));
    ReifiedArtifact intermediate = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar"), new Version("1.0.0"));
    ReifiedArtifact intermediate2 = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "intermediate2", "intermediate2", "jar"), new Version("1.0.0"));
    ReifiedArtifact multipleVersions = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions", "multiple-versions", "jar"), new Version("1.1.0"));
    ReifiedArtifact multipleVersionsDifferentDeps = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar"), new Version("1.1.0"));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    graph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile"));
    graph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime"));
    graph.addEdge(new Dependency(intermediate2.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("2.0.0"), new Version("2.0.0"), "runtime"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersions.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "runtime"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(intermediate2.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersions, "compile");
    expected.addEdge(project, intermediate, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDeps, "compile");
    expected.addEdge(intermediate, multipleVersions, "compile");
    expected.addEdge(intermediate, multipleVersionsDifferentDeps, "runtime");
    expected.addEdge(multipleVersions, intermediate2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, intermediate2, "compile");
    expected.addEdge(multipleVersionsDifferentDeps, leaf, "compile");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf
   *              |              ^                          (1.0.0) (2.0.0)
   *              |              |                              ^      ^
   *              |->(1.0.0)intermediate                        |      |
   *              |              |                              |      |
   *              |             \/                              |      |
   *              |          (1.1.0)                        (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will encounter two incompatible versions. However, we have set skipCompatibilityCheck to
   * true, which should upgrade to 2.0.0.
   */
  @Test
  public void reduceFailureButSkipCompatibilityCheck() {
    ArtifactID leaf = new ArtifactID("org.lattejava.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar");
    ArtifactID multipleVersions = new ArtifactID("org.lattejava.test", "multiple-versions", "multiple-versions", "jar");
    ArtifactID multipleVersionsDifferentDeps = new ArtifactID("org.lattejava.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(project.id), new Dependency(intermediate), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile"));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime"));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile"));

    // Add the skip node
    incompatible.addEdge(new Dependency(project.id), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "runtime"));
    incompatible.skipCompatibilityCheck(leaf);

    ReifiedArtifact intermediateArtifact = new ReifiedArtifact(intermediate, new Version("1.0.0"));
    ReifiedArtifact multipleVersionsArtifact = new ReifiedArtifact(multipleVersions, new Version("1.1.0"));
    ReifiedArtifact multipleVersionsDifferentDepsArtifact = new ReifiedArtifact(multipleVersionsDifferentDeps, new Version("1.1.0"));
    ReifiedArtifact leafArtifact = new ReifiedArtifact(leaf, new Version("2.0.0"));
    ArtifactGraph actual = service.reduce(incompatible);
    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, multipleVersionsArtifact, "compile");
    expected.addEdge(project, intermediateArtifact, "runtime");
    expected.addEdge(project, multipleVersionsDifferentDepsArtifact, "compile");
    expected.addEdge(project, leafArtifact, "runtime");
    expected.addEdge(intermediateArtifact, multipleVersionsArtifact, "compile");
    expected.addEdge(intermediateArtifact, multipleVersionsDifferentDepsArtifact, "runtime");
    expected.addEdge(multipleVersionsArtifact, leafArtifact, "compile");
    expected.addEdge(multipleVersionsDifferentDepsArtifact, leafArtifact, "compile");
    assertEquals(actual, expected);
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)leaf
   *              |     * (2.0.0)
   *              |          ^
   *              |          |
   *              |          |
   *              |->(1.0.0)intermediate
   * </pre>
   * <p>
   * Notice that leaf has two versions, 1.0.0 and 2.0.0. Since this artifact is reachable from the root node, it will
   * cause a failure.
   */
  @Test
  public void reduceFailureFromRoot() {
    ArtifactID leaf = new ArtifactID("org.lattejava.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(new Dependency(project.id), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    incompatible.addEdge(new Dependency(project.id), new Dependency(intermediate), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.0.0"), "compile"));

    try {
      service.reduce(incompatible);
      fail("Should have failed");
    } catch (CompatibilityException e) {
      assertEquals(e.dependency.id, leaf);
      assertEquals(e.min, new Version("1.0.0"));
      assertEquals(e.max, new Version("2.0.0"));
    }
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *   root(1.0.0)-->(1.0.0)multiple-versions(1.0.0)-->(1.0.0)leaf:leaf
   *              |            (1.1.0)       (1.1.0)-->(1.0.0)leaf:leaf
   *              |              ^                          (1.0.0) (2.0.0)
   *              |              |                              ^      ^
   *              |->(1.0.0)intermediate                        |      |
   *              |              |                              |      |
   *              |             \/                              |      |
   *              |          (1.1.0)                        (1.0.0) (1.1.0)
   *              |->(1.0.0)multiple-versions-different-dependencies
   *              |
   *              |
   *              |
   *              |
   * </pre>
   * <p>
   * Notice that the leaf has two versions, 1.0.0 and 2.0.0. Since the first visit to this node from the
   * multiple-versions node will encounter two incompatible versions, it will cause a failure.
   */
  @Test
  public void reduceFailureNested() {
    ArtifactID leaf = new ArtifactID("org.lattejava.test", "leaf", "leaf", "jar");
    ArtifactID intermediate = new ArtifactID("org.lattejava.test", "intermediate", "intermediate", "jar");
    ArtifactID multipleVersions = new ArtifactID("org.lattejava.test", "multiple-versions", "multiple-versions", "jar");
    ArtifactID multipleVersionsDifferentDeps = new ArtifactID("org.lattejava.test", "multiple-versions-different-dependencies", "multiple-versions-different-dependencies", "jar");

    DependencyGraph incompatible = new DependencyGraph(project);
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(project.id), new Dependency(intermediate), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    incompatible.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersions), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile"));
    incompatible.addEdge(new Dependency(intermediate), new Dependency(multipleVersionsDifferentDeps), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime"));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(multipleVersions), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile"));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime"));
    incompatible.addEdge(new Dependency(multipleVersionsDifferentDeps), new Dependency(leaf), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.0.0"), "compile"));

    try {
      service.reduce(incompatible);
      fail("Should have failed");
    } catch (CompatibilityException e) {
      assertEquals(e.dependency.id, leaf);
      assertEquals(e.min, new Version("1.0.0"));
      assertEquals(e.max, new Version("2.0.0"));
    }
  }

  /**
   * Graph:
   * <p>
   * <pre>
   *    Project(1.0.0)------------------------------------------------------------------------|
   *              |                                                                        (1.0.0)
   *              |                                                                           A (1.0.0) -----> (1.0.0) A-child
   *              |                                                                        (1.0.0)
   *              |                                                                           ^
   *              |-------------> (1.1.0)B(1.0.0) -----------> (1.0.0)D(1.0.0)----------------|
   *              |                   (1.0.0)
   *              |                      C
   *              |                   (1.0.0)
   *              |----------------------|
   * </pre>
   * <p>
   */
  @Test
  public void reduceLastPathIsPruned() {
    ReifiedArtifact a = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "a", "a", "jar"), new Version("1.0.0"));
    ReifiedArtifact aChild = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "a-child", "a-child", "jar"), new Version("1.0.0"));
    ReifiedArtifact b = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "b", "b", "jar"), new Version("1.1.0"));
    ReifiedArtifact c = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "c", "c", "jar"), new Version("1.0.0"));
    ReifiedArtifact d = new ReifiedArtifact(new ArtifactID("org.lattejava.test", "d", "d", "jar"), new Version("1.1.0"));

    DependencyGraph graph = new DependencyGraph(project);
    graph.addEdge(new Dependency(project.id), new Dependency(a.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(project.id), new Dependency(b.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile"));
    graph.addEdge(new Dependency(project.id), new Dependency(c.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(a.id), new Dependency(aChild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(b.id), new Dependency(d.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));
    graph.addEdge(new Dependency(d.id), new Dependency(a.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile"));

    ArtifactGraph expected = new ArtifactGraph(project);
    expected.addEdge(project, a, "compile");
    expected.addEdge(project, b, "compile");
    expected.addEdge(project, c, "compile");
    expected.addEdge(a, aChild, "compile");

    ArtifactGraph actual = service.reduce(graph);
    assertEquals(actual, expected);
  }

  @Test
  public void reduceSimple() {
    ArtifactGraph actual = service.reduce(goodGraph);
    assertEquals(actual, goodReducedGraph);
  }

  @Test
  public void resolveGraph() {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("compile", new GroupTraversalRule(true, true))
                            .with("runtime", new GroupTraversalRule(true, true))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedMultipleVersions, "compile");
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");
    expected.addEdge(projectResolved, resolvedMultipleVersionsDifferentDeps, "compile");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersions, "compile");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersionsDifferentDeps, "runtime");
    expected.addEdge(resolvedMultipleVersions, resolvedLeaf1, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedIntegrationBuild, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf1_1, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf2_2, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf3_3, "runtime");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);

    String expectedClasspath = String.join(File.pathSeparator, resolvedIntermediate.file.toAbsolutePath().toString(),
        resolvedMultipleVersions.file.toAbsolutePath().toString(), resolvedLeaf1.file.toAbsolutePath().toString(),
        resolvedIntegrationBuild.file.toAbsolutePath().toString(), resolvedMultipleVersionsDifferentDeps.file.toAbsolutePath().toString(),
        resolvedLeaf1_1.file.toAbsolutePath().toString(), resolvedLeaf2_2.file.toAbsolutePath().toString(),
        resolvedLeaf3_3.file.toAbsolutePath().toString());
    assertEquals(actual.toClasspath().toString(), expectedClasspath);
  }

  @Test
  public void resolveGraphFailureBadLicense() {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    try {
      service.resolve(artifactGraph, workflow,
          new TraversalRules().with("compile", new GroupTraversalRule(true, true, License.Licenses.get("GPLV2_0")))
                              .with("runtime", new GroupTraversalRule(true, true))
      );
    } catch (LicenseException e) {
      assertEquals(e.artifact, leaf1);
    }
  }

  @Test
  public void resolveGraphFailureMD5() {
    DependencyGraph graph = makeSimpleGraph("org.lattejava.test:bad-md5:1.0.0");
    try {
      ArtifactGraph artifactGraph = service.reduce(graph);
      service.resolve(artifactGraph, workflow, new TraversalRules().with("compile", new GroupTraversalRule(true, true)));
    } catch (MD5Exception e) {
      assertEquals(e.getMessage(), "MD5 mismatch when fetching item from [http://localhost:7042/test-deps/savant/org/savantbuild/test/bad-md5/1.0.0/bad-md5-1.0.0.jar]");
    }
  }

  @Test
  public void resolveGraphFailureMissingDependency() {
    DependencyGraph graph = makeSimpleGraph("org.lattejava.test:missing-item:1.0.0");
    try {
      ArtifactGraph artifactGraph = service.reduce(graph);
      service.resolve(artifactGraph, workflow, new TraversalRules().with("compile", new GroupTraversalRule(true, true)));
    } catch (ArtifactMissingException e) {
      assertEquals(e.artifact, new Artifact("org.lattejava.test:missing-item:1.0.0"));
    }
  }

  @Test
  public void resolveGraphNonTransitiveSpecificGroups() {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("compile", new GroupTraversalRule(true, false))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    ResolvedArtifact multipleVersions = new ResolvedArtifact("org.lattejava.test:multiple-versions:1.1.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")), cache.resolve("org/savantbuild/test/multiple-versions/1.1.0/multiple-versions-1.1.0.jar").toAbsolutePath(), null);
    ResolvedArtifact multipleVersionsDifferentDeps = new ResolvedArtifact("org.lattejava.test:multiple-versions-different-dependencies:1.1.0", Collections.singletonList(License.Licenses.get("ApacheV2_0")), cache.resolve("org/savantbuild/test/multiple-versions-different-dependencies/1.1.0/multiple-versions-different-dependencies-1.1.0.jar").toAbsolutePath(), null);

    expected.addEdge(projectResolved, multipleVersions, "compile");
    expected.addEdge(projectResolved, multipleVersionsDifferentDeps, "compile");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void resolveGraphTransitiveWithTransitiveGroups() {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("runtime", new GroupTraversalRule(true, "compile", "runtime"))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersions, "compile");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersionsDifferentDeps, "runtime");
    expected.addEdge(resolvedMultipleVersions, resolvedLeaf1, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedIntegrationBuild, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf1_1, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf2_2, "compile");
    expected.addEdge(resolvedMultipleVersionsDifferentDeps, resolvedLeaf3_3, "runtime");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void resolveGraphTransitiveWithTransitiveMissingGroups() {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("runtime", new GroupTraversalRule(true, "missing"))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void resolveGraphTransitiveWithTransitiveSingleGroup() {
    ArtifactGraph artifactGraph = service.reduce(goodGraph);
    ResolvedArtifactGraph actual = service.resolve(artifactGraph, workflow,
        new TraversalRules().with("runtime", new GroupTraversalRule(true, "compile"))
    );

    ResolvedArtifactGraph expected = new ResolvedArtifactGraph(projectResolved);
    expected.addEdge(projectResolved, resolvedIntermediate, "runtime");
    expected.addEdge(resolvedIntermediate, resolvedMultipleVersions, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedLeaf1, "compile");
    expected.addEdge(resolvedMultipleVersions, resolvedIntegrationBuild, "compile");

    assertEquals(actual, expected);

    verifyResolvedArtifacts(actual);
  }

  @Test
  public void shortNonSemanticVersions() {
    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:has-short-non-semantic-versioned-dep:1.0.0")
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "http://localhost:7042/test-deps/maven", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mavenCache.toString())
        ),
        output
    );
    workflow.mappings.put("org.lattejava.test:badver:1.0", new Version("1.0.0"));

    ArtifactID hasNonSemanticId = new ArtifactID("org.lattejava.test:has-short-non-semantic-versioned-dep");
    ArtifactID badVerId = new ArtifactID("org.lattejava.test:badver");
    DependencyGraph expected = new DependencyGraph(project);
    expected.addEdge(new Dependency(project.id), new Dependency(hasNonSemanticId), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("GPLV2_0", null)));
    expected.addEdge(new Dependency(hasNonSemanticId), new Dependency(badVerId), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Apache-2.0", null)));
    expected.addEdge(new Dependency(badVerId), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));

    var result = buildAndResolve();
    assertEquals(result.dependencyGraph, expected);

    ReifiedArtifact badVer = result.artifactGraph.values()
                                                 .stream()
                                                 .filter(r -> r.id.name.equals("badver"))
                                                 .findFirst()
                                                 .orElseThrow();
    assertEquals(badVer.nonSemanticVersion, "1.0");
  }

  @Test
  public void topLevelNonSemanticVersions() {
    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:badver:1.0.0", "1.0.0.Final", false, List.of())
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "http://localhost:7042/test-deps/maven", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mavenCache.toString())
        ),
        output
    );
    workflow.mappings.put("org.lattejava.test:badver:1.0.0.Final", new Version("1.0.0"));

    ArtifactID badVerId = new ArtifactID("org.lattejava.test:badver");
    DependencyGraph expected = new DependencyGraph(project);
    expected.addEdge(new Dependency(project.id), new Dependency(badVerId), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Apache-2.0", null)));
    expected.addEdge(new Dependency(badVerId), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));

    var result = buildAndResolve();
    assertEquals(result.dependencyGraph, expected);

    ReifiedArtifact badVer = result.artifactGraph.values()
                                                 .stream()
                                                 .filter(r -> r.id.name.equals("badver"))
                                                 .findFirst()
                                                 .orElseThrow();
    assertEquals(badVer.nonSemanticVersion, "1.0.0.Final");
  }

  @Test
  public void topLevelShortNonSemanticVersions() {
    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:badver:1.0.0", "1.0", false, List.of())
        )
    );

    workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mavenCache.toString()),
            new URLProcess(output, "http://localhost:7042/test-deps/savant", null, null),
            new MavenProcess(output, "http://localhost:7042/test-deps/maven", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mavenCache.toString())
        ),
        output
    );
    workflow.mappings.put("org.lattejava.test:badver:1.0", new Version("1.0.0"));

    ArtifactID badVerId = new ArtifactID("org.lattejava.test:badver");
    DependencyGraph expected = new DependencyGraph(project);
    expected.addEdge(new Dependency(project.id), new Dependency(badVerId), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Apache-2.0", null)));
    expected.addEdge(new Dependency(badVerId), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));

    var result = buildAndResolve();
    assertEquals(result.dependencyGraph, expected);

    ReifiedArtifact badVer = result.artifactGraph.values()
                                                 .stream()
                                                 .filter(r -> r.id.name.equals("badver"))
                                                 .findFirst()
                                                 .orElseThrow();
    assertEquals(badVer.nonSemanticVersion, "1.0");
  }

  @Test
  public void transitiveAndDirectExclusions() {
    // Override to add exclusions but notice that the exclusions are brought back in because the intermediate pulls leaf1 transitively back in through multipleVersions.
    // The only exclusion that survives is through intermediate's exclusion of leaf2_2 in the main project build file
    goodGraph = new DependencyGraph(project);
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV1_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(intermediate.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(leaf1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersions.id), new Dependency(integrationBuild.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("LGPLV2_1")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf3_3.id), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    // Excluded
    //  * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(leaf1.id, leaf1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(integrationBuild.id, integrationBuild.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id, multipleVersionsDifferentDeps.nonSemanticVersion), new Dependency(leaf2_2.id, leaf2_2.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("OtherNonDistributableOpenSource", "Open source")));

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(multipleVersions.id, new Version("1.0.0"), Collections.singletonList(leaf1.id)),
            new Artifact(multipleVersionsDifferentDeps.id, new Version("1.0.0"))
        ),
        new DependencyGroup("runtime", true,
            new Artifact(intermediate.id, new Version("1.0.0"), Arrays.asList(integrationBuild.id, leaf2_2.id))
        )
    );

    var result = buildAndResolve();
    assertEquals(result.dependencyGraph, goodGraph);
  }

  @Test
  public void wildcardExclusions() {
    // Override to add exclusions but notice that the exclusions are brought back in because the intermediate pulls leaf1 transitively back in through multipleVersions.
    // The only exclusion that survives is through intermediate's exclusion of leaf2_2 in the main project build file
    goodGraph = new DependencyGraph(project);
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersions.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV1_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(intermediate.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(project.id), new Dependency(multipleVersionsDifferentDeps.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("ApacheV2_0")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf2.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "runtime", License.Licenses.get("LGPLV2_1")));
    goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id), new Dependency(leaf1_1.id), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    // Excluded
    //  * goodGraph.addEdge(new Dependency(intermediate.id, intermediate.nonSemanticVersion), new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "compile", License.Licenses.get("ApacheV2_0")));
    //  * goodGraph.addEdge(new Dependency(intermediate.id, intermediate.nonSemanticVersion), new Dependency(multipleVersionsDifferentDeps.id, multipleVersionsDifferentDeps.nonSemanticVersion), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.1.0"), "runtime", License.Licenses.get("ApacheV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(leaf1.id, leaf1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(leaf1.id, leaf1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.0.0"), new Version("1.0.0"), "compile", License.Licenses.get("GPLV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(integrationBuild.id, integrationBuild.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersions.id, multipleVersions.nonSemanticVersion), new Dependency(integrationBuild.id, integrationBuild.nonSemanticVersion), new DependencyEdgeValue(new Version("1.0.0"), new Version("2.1.1-{integration}"), "compile", License.Licenses.get("ApacheV2_0")));
    //  * goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id, multipleVersionsDifferentDeps.nonSemanticVersion), new Dependency(leaf2_2.id, leaf2_2.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("OtherNonDistributableOpenSource", "Open source")));
    //  * goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id, multipleVersionsDifferentDeps.nonSemanticVersion), new Dependency(leaf1_1.id, leaf1_1.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "compile", License.parse("Commercial", "Commercial license")));
    //  * goodGraph.addEdge(new Dependency(multipleVersionsDifferentDeps.id, multipleVersionsDifferentDeps.nonSemanticVersion), new Dependency(leaf3_3.id, leaf3_3.nonSemanticVersion), new DependencyEdgeValue(new Version("1.1.0"), new Version("1.0.0"), "runtime", License.Licenses.get("ApacheV2_0")));

    dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(multipleVersions.id, new Version("1.0.0"), Collections.singletonList(new ArtifactID("*:*:*:*"))),
            new Artifact(multipleVersionsDifferentDeps.id, new Version("1.0.0"))
        ),
        new DependencyGroup("runtime", true,
            new Artifact(intermediate.id, new Version("1.0.0"), Collections.singletonList(new ArtifactID("*:*:*:*")))
        )
    );

    DependencyGraph actual = service.buildGraph(project, dependencies, workflow);
    assertEquals(actual, goodGraph);
  }

  private ResolveResult buildAndResolve() {
    var graph = service.buildGraph(project, dependencies, workflow);
    var reduced = service.reduce(graph);
    var rules = new TraversalRules().with("compile", new GroupTraversalRule(true, true));
    var resolved = service.resolve(reduced, workflow, rules);
    return new ResolveResult(graph, reduced, resolved);
  }

  private Dependencies makeSimpleDependencies(String dependency) {
    return new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact(dependency)
        )
    );
  }

  /**
   * Builds a simple DependencyGraph that only contains an edge from the project to a single dependency.
   *
   * @param dependency The dependency.
   * @return The graph.
   */
  private DependencyGraph makeSimpleGraph(String dependency) {
    DependencyGraph graph = new DependencyGraph(project);
    Artifact artifact = new Artifact(dependency);
    graph.addEdge(new Dependency(project.id), new Dependency(artifact.id), new DependencyEdgeValue(project.version, artifact.version, "compile"));
    return graph;
  }

  private void verifyResolvedArtifacts(ResolvedArtifactGraph actual) {
    // Verify that all the artifacts have files, and they all exist (except for the project)
    Set<ResolvedArtifact> artifacts = actual.values();
    artifacts.remove(projectResolved);
    artifacts.forEach((artifact) -> assertTrue(Files.isRegularFile(artifact.file)));
  }

  record ResolveResult(DependencyGraph dependencyGraph, ArtifactGraph artifactGraph,
                       ResolvedArtifactGraph resolvedArtifactGraph) {
  }
}
