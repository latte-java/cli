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
package org.lattejava.cli.parser.groovy;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.Publication;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.workflow.process.CacheProcess;
import org.lattejava.dep.workflow.process.MavenProcess;
import org.lattejava.dep.workflow.process.S3Process;
import org.lattejava.dep.workflow.process.URLProcess;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.domain.Publications;
import org.lattejava.cli.domain.Target;
import org.lattejava.domain.Version;
import org.lattejava.cli.parser.DefaultTargetGraphBuilder;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.util.Graph;
import org.lattejava.util.HashGraph;
import org.lattejava.util.LattePaths;
import org.testng.annotations.Test;

import groovy.lang.MissingPropertyException;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the groovy project file parser.
 *
 * @author Brian Pontarelli
 */
public class GroovyProjectFileParserTest extends BaseUnitTest {
  @Test
  public void parse() {
    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    Path buildFile = projectDir.resolve("src/test/java/org/lattejava/cli/parser/groovy/simple.latte");
    Project project = parser.parse(buildFile, new RuntimeConfiguration());
    assertEquals(project.group, "group");
    assertEquals(project.name, "name");
    assertEquals(project.version, new Version("1.1.0"));
    assertEquals(project.licenses.get(0).identifier, "Apache-2.0");
    assertEquals(project.licenses.get(1).identifier, "Apache-1.0");
    assertEquals(project.licenses.get(2).identifier, "BSD-2-Clause");
    assertEquals(project.licenses.get(2).text, "BSD license");
    assertEquals(project.licenses.get(3).identifier, "Commercial");
    assertEquals(project.licenses.get(3).text, "Commercial license file.");

    // Verify the targets
    assertEquals(project.targets.get("compile").name, "compile");
    assertEquals(project.targets.get("compile").description, "This target compiles everything");
    assertNotNull(project.targets.get("compile").invocation);
    assertNull(project.targets.get("compile").dependencies);

    assertEquals(project.targets.get("test").name, "test");
    assertEquals(project.targets.get("test").description, "This runs the tests");
    assertNotNull(project.targets.get("test").invocation);
    assertEquals(project.targets.get("test").dependencies, Collections.singletonList("compile"));

    // Verify the target graph
    Graph<Target, Object> expected = new HashGraph<>();
    expected.addEdge(project.targets.get("test"), project.targets.get("compile"), Project.GRAPH_EDGE);
    assertEquals(project.targetGraph, expected);

    // Verify the target executes correctly
    project.targets.get("compile").invocation.run();
    assertEquals(project.name, "changed");

    // Verify the workflow
    assertEquals(project.workflow.fetchWorkflow.processes.size(), 4);
    assertTrue(project.workflow.fetchWorkflow.processes.get(0) instanceof CacheProcess);
    assertEquals(((CacheProcess) project.workflow.fetchWorkflow.processes.get(0)).latteDir, LattePaths.get().cacheDir().toString());
    assertEquals(((CacheProcess) project.workflow.fetchWorkflow.processes.get(0)).integrationDir, LattePaths.get().cacheDir().toString());
    assertEquals(((CacheProcess) project.workflow.fetchWorkflow.processes.get(0)).mavenDir, System.getProperty("user.home") + "/.m2/repository");
    assertTrue(project.workflow.fetchWorkflow.processes.get(1) instanceof CacheProcess);
    assertEquals(((CacheProcess) project.workflow.fetchWorkflow.processes.get(1)).mavenDir, System.getProperty("user.home") + "/.m2/repository");
    assertEquals(((CacheProcess) project.workflow.fetchWorkflow.processes.get(1)).integrationDir, LattePaths.get().cacheDir().toString());
    assertEquals(((URLProcess) project.workflow.fetchWorkflow.processes.get(2)).url, "https://repository.lattejava.org");
    assertEquals(((URLProcess) project.workflow.fetchWorkflow.processes.get(2)).username, "username");
    assertEquals(((URLProcess) project.workflow.fetchWorkflow.processes.get(2)).password, "password");
    assertEquals(((MavenProcess) project.workflow.fetchWorkflow.processes.get(3)).url, "https://repo1.maven.org/maven2");
    assertEquals(((MavenProcess) project.workflow.fetchWorkflow.processes.get(3)).username, "username");
    assertEquals(((MavenProcess) project.workflow.fetchWorkflow.processes.get(3)).password, "password");
    assertEquals(project.workflow.publishWorkflow.processes.size(), 2);
    assertEquals(((CacheProcess) project.workflow.publishWorkflow.processes.get(0)).latteDir, LattePaths.get().cacheDir().toString());
    assertEquals(((CacheProcess) project.workflow.publishWorkflow.processes.get(0)).integrationDir, LattePaths.get().cacheDir().toString());
    assertEquals(((CacheProcess) project.workflow.publishWorkflow.processes.get(0)).mavenDir, System.getProperty("user.home") + "/.m2/repository");
    assertEquals(((CacheProcess) project.workflow.publishWorkflow.processes.get(1)).mavenDir, System.getProperty("user.home") + "/.m2/repository");
    assertEquals(((CacheProcess) project.workflow.publishWorkflow.processes.get(1)).integrationDir, LattePaths.get().cacheDir().toString());

    // Version mappings
    Map<String, Version> expectedMappings = new HashMap<>();
    expectedMappings.put("org.example:non-semantic-version:1.0.0.Final", new Version("1.0.0"));
    expectedMappings.put("org.example:short-non-semantic-version:1.0", new Version("1.0.0"));
    assertEquals(project.workflow.mappings, expectedMappings);

    // Verify the PublishWorkflow (removed — no publishWorkflow block in simple.latte)
    assertNull(project.publishWorkflow);

    // Verify the dependencies
    final List<ArtifactID> exclusions = asList(
        new ArtifactID("org.example", "exclude", "exclude", "jar"),
        new ArtifactID("org.example", "exclude-2", "exclude-2", "zip"),
        new ArtifactID("org.example", "exclude-3", "exclude-4", "xml")
    );
    Dependencies expectedDependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.example:compile:1.0.0", null, false, exclusions),
            new Artifact(new ArtifactID("org.example:short-non-semantic-version"), new Version("1.0.0"), "1.0", Collections.emptyList())
        ),
        new DependencyGroup("test-compile", false, new Artifact("org.example:test:1.0.0"), new Artifact("org.example:test2:2.0.0")));
    assertEquals(project.dependencies, expectedDependencies);

    var nonSemanticVersionedArtifact = project.dependencies
        .groups
        .get("compile")
        .dependencies
        .stream()
        .filter(d -> Objects.equals(d.id, new ArtifactID("org.example:short-non-semantic-version")))
        .findFirst()
        .orElseThrow();
    assertEquals(nonSemanticVersionedArtifact.nonSemanticVersion, "1.0");

    // Verify the publications
    List<License> licenses = Arrays.asList(
        License.parse("Apache-2.0", null),
        License.parse("Apache-1.0", null),
        License.parse("BSD-2-Clause", "BSD license"),
        License.parse("Commercial", "Commercial license file.")
    );
    Publications expectedPublications = new Publications();
    expectedPublications.add("main",
        new Publication(new ReifiedArtifact(new ArtifactID("group", "name", "publication1", "jar"), new Version("1.1.0"), licenses),
            new ArtifactMetaData(expectedDependencies, licenses),
            buildFile.getParent().resolve("build/jars/name-1.1.0.jar").toAbsolutePath(),
            buildFile.getParent().resolve("build/jars/name-1.1.0-src.jar").toAbsolutePath())
    );
    expectedPublications.add("main",
        new Publication(new ReifiedArtifact(new ArtifactID("group", "name", "publication3", "jar"), new Version("1.1.0"), licenses),
            new ArtifactMetaData(new Dependencies(), licenses),
            buildFile.getParent().resolve("build/jars/name-1.1.0.jar").toAbsolutePath(),
            buildFile.getParent().resolve("build/jars/name-1.1.0-src.jar").toAbsolutePath())
    );
    expectedPublications.add("test",
        new Publication(new ReifiedArtifact(new ArtifactID("group", "name", "publication2", "jar"), new Version("1.1.0"), licenses),
            new ArtifactMetaData(expectedDependencies, licenses),
            buildFile.getParent().resolve("build/jars/name-test-1.1.0.jar").toAbsolutePath(),
            buildFile.getParent().resolve("build/jars/name-test-1.1.0-src.jar").toAbsolutePath())
    );
    assertEquals(project.publications, expectedPublications);
  }

  @Test
  public void parseMissingPlugin() {
    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    Path buildFile = projectDir.resolve("src/test/java/org/lattejava/cli/parser/groovy/missing-plugin.latte");
    Project project = parser.parse(buildFile, new RuntimeConfiguration());

    try {
      project.targets.get("compile").invocation.run();
    } catch (MissingPropertyException e) {
      // Expected
      assertTrue(e.getMessage().contains("property [missingDependency]"));
    }
  }

  @Test
  public void parseNonSemanticVersion() {
    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    Path buildFile = projectDir.resolve("src/test/java/org/lattejava/cli/parser/groovy/non-semantic-version.latte");
    try {
      parser.parse(buildFile, new RuntimeConfiguration());
    } catch (ParseException e) {
      // Expected
      assertTrue(e.getMessage().contains("1.0.0.Final"));
      assertTrue(e.getMessage().contains("semanticVersions"));
    }
  }

  @Test
  public void parseShortNonSemanticVersion() {
    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    Path buildFile = projectDir.resolve("src/test/java/org/lattejava/cli/parser/groovy/short-non-semantic-version.latte");
    Project project = parser.parse(buildFile, new RuntimeConfiguration());

    // Verify the dependencies
    Dependencies expectedDependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.example:short-non-semantic-version:1.0.0", null, false, List.of())
        )
    );
    assertEquals(project.dependencies, expectedDependencies);

    var nonSemanticVersionedArtifact = project.dependencies
        .groups
        .get("compile")
        .dependencies
        .get(0);
    assertEquals(nonSemanticVersionedArtifact.nonSemanticVersion, "1.0");
  }

  @Test
  public void parseS3Workflow() {
    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    Path buildFile = projectDir.resolve("src/test/java/org/lattejava/cli/parser/groovy/s3-workflow.latte");
    Project project = parser.parse(buildFile, new RuntimeConfiguration());

    // Verify fetch workflow has cache + s3
    assertEquals(project.workflow.fetchWorkflow.processes.size(), 2);
    assertTrue(project.workflow.fetchWorkflow.processes.get(0) instanceof CacheProcess);
    assertTrue(project.workflow.fetchWorkflow.processes.get(1) instanceof S3Process);

    S3Process fetchS3 = (S3Process) project.workflow.fetchWorkflow.processes.get(1);
    assertEquals(fetchS3.endpoint, "https://abc123.r2.cloudflarestorage.com");
    assertEquals(fetchS3.bucket, "my-repo");

    // Verify publish workflow has s3 with explicit region
    assertEquals(project.workflow.publishWorkflow.processes.size(), 1);
    assertTrue(project.workflow.publishWorkflow.processes.get(0) instanceof S3Process);

    S3Process publishS3 = (S3Process) project.workflow.publishWorkflow.processes.get(0);
    assertEquals(publishS3.endpoint, "https://abc123.r2.cloudflarestorage.com");
    assertEquals(publishS3.bucket, "my-repo");
  }

  @Test
  public void parseWithSwitches() {
    GroovyProjectFileParser parser = new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder());
    Path buildFile = projectDir.resolve("src/test/java/org/lattejava/cli/parser/groovy/simple.latte");
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration();
    runtimeConfiguration.switches.add("skip");
    Project project = parser.parse(buildFile, runtimeConfiguration);

    // Verify the target executes correctly
    project.targets.get("compile").invocation.run();
    assertEquals(project.name, "name");
  }
}
