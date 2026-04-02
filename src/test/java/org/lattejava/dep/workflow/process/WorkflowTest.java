/*
 * Copyright (c) 2022-2024, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep.workflow.process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.PathTools;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.workflow.FetchWorkflow;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.domain.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the Workflow for fetching artifacts, specifically the Maven handling.
 *
 * @author Brian Pontarelli
 */
public class WorkflowTest extends BaseUnitTest {
  @Test
  public void fetchSource_empty_publish_workflow() throws Exception {
    // arrange — publish workflow is empty, so fetched files won't be cached
    Path cache = projectDir.resolve("build/test/cache");
    Path mvnCache = projectDir.resolve("build/test/maven-cache");
    PathTools.prune(cache);
    PathTools.prune(mvnCache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mvnCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));

    // act
    var sourcePath = workflow.fetchSource(artifact);

    // assert — source is found from Maven Central even though it can't be cached
    assertNotNull(sourcePath);
  }

  @Test
  public void fetchSource_publish_source_file_exists() throws Exception {
    // arrange
    Path cache = projectDir.resolve("build/test/cache");
    Path mvnCache = projectDir.resolve("build/test/maven-cache");
    PathTools.prune(cache);
    PathTools.prune(mvnCache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mvnCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mvnCache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));

    // act
    var sourcePath = workflow.fetchSource(artifact);

    // assert
    // Source file is fetched from Maven (tagged MAVEN) and published to the Maven cache — no renaming
    assertEquals(sourcePath.toString(), "build/test/maven-cache/org/apache/groovy/groovy/4.0.5/groovy-4.0.5-sources.jar");
  }

  @Test
  public void fetchSource_publish_source_file_semantic_mapping_exists() throws IOException {
    // arrange
    Path cache = projectDir.resolve("build/test/cache");
    Path mvnCache = projectDir.resolve("build/test/maven-cache");
    PathTools.prune(cache);
    PathTools.prune(mvnCache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mvnCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mvnCache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact(new ArtifactID("org.xerial.snappy:snappy-java:snappy-java:jar"),
        new Version("1.1.10+5"),
        "1.1.10.5",
        List.of(License.Licenses.get("Apache-2.0")));

    // act
    var sourcePath = workflow.fetchSource(artifact);

    // assert
    // Source file is fetched from Maven using non-semantic version — no renaming
    assertEquals(sourcePath.toString(), "build/test/maven-cache/org/xerial/snappy/snappy-java/1.1.10.5/snappy-java-1.1.10.5-sources.jar");
  }

  @Test
  public void mavenCentral() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    Path mvnCache = projectDir.resolve("build/test/maven-cache");
    PathTools.prune(cache);
    PathTools.prune(mvnCache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mvnCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mvnCache.toString())
        ),
        output
    );

    Artifact artifact = new ReifiedArtifact("org.apache.groovy:groovy:4.0.5", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd = workflow.fetchMetaData(artifact);
    assertNotNull(amd);

    // Everything is optional, so it should be an empty dependencies
    Dependencies expected = new Dependencies();
    assertEquals(amd.dependencies, expected);

    // POMs are Maven-sourced and go to the Maven cache
    assertTrue(Files.isRegularFile(mvnCache.resolve("org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom")));
    assertTrue(Files.isRegularFile(mvnCache.resolve("org/apache/groovy/groovy/4.0.5/groovy-4.0.5.pom.md5")));

    // AMDs are no longer written to disk — POM is translated in memory
    assertFalse(Files.exists(cache.resolve("org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd")));
    assertFalse(Files.exists(cache.resolve("org/apache/groovy/groovy/4.0.5/groovy-4.0.5.jar.amd.md5")));
  }

  @Test
  public void mavenCentralMapping() throws Exception {
    Path cache = projectDir.resolve("build/test/cache");
    Path mvnCache = projectDir.resolve("build/test/maven-cache");
    PathTools.prune(cache);
    PathTools.prune(mvnCache);

    Workflow workflow = new Workflow(
        new FetchWorkflow(
            output,
            new CacheProcess(output, cache.toString(), null, mvnCache.toString()),
            new MavenProcess(output, "https://repo1.maven.org/maven2", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cache.toString(), null, mvnCache.toString())
        ),
        output
    );
    workflow.mappings.put("io.netty:netty-all:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-buffer:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-codec-http2:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-common:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-handler-proxy:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-resolver-dns:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-tcnative-boringssl-static:2.0.39.Final", new Version("2.0.39"));
    workflow.mappings.put("io.netty:netty-transport:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-epoll:4.1.65.Final", new Version("4.1.65"));
    workflow.mappings.put("io.netty:netty-transport-native-kqueue:4.1.65.Final", new Version("4.1.65"));

    Artifact artifact = new ReifiedArtifact("io.vertx:vertx-core:3.9.8", License.Licenses.get("Apache-2.0"));
    ArtifactMetaData amd = workflow.fetchMetaData(artifact);
    assertNotNull(amd);

    Dependencies expected = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("io.netty:netty-common:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-buffer:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-transport:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-handler:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-handler-proxy:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-codec-http:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-codec-http2:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-resolver:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("io.netty:netty-resolver-dns:4.1.65", "4.1.65.Final", false, Collections.emptyList()),
            new Artifact("com.fasterxml.jackson.core:jackson-core:2.11.3"),
            new Artifact("com.fasterxml.jackson.core:jackson-databind:2.11.3")
        )
    );
    assertEquals(amd.dependencies, expected);

    // POMs are Maven-sourced and go to the Maven cache
    assertTrue(Files.isRegularFile(mvnCache.resolve("io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.pom")));
    assertTrue(Files.isRegularFile(mvnCache.resolve("io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.pom.md5")));

    // AMDs are no longer written to disk — POM is translated in memory
    assertFalse(Files.exists(cache.resolve("io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.jar.amd")));
    assertFalse(Files.exists(cache.resolve("io/vertx/vertx-core/3.9.8/vertx-core-3.9.8.jar.amd.md5")));
  }
}
