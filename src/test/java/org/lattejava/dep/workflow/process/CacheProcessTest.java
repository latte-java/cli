/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.PathTools;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.domain.ResolvableItem;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * This class is the test for the CacheProcess.
 *
 * @author Brian Pontarelli
 */
public class CacheProcessTest extends BaseUnitTest {
  @Test
  public void fetch() {
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("test-deps/latte/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void fetchIntegration() {
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/integration").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:integration-build:integration-build:2.1.1-{integration}:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("test-deps/integration/org/lattejava/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void store() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/latte/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = process.publish(new FetchResult(artFile, ItemSource.LATTE, item));
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("build/test/deps/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  public void store_rejectsMaven() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/latte/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path result = process.publish(new FetchResult(artFile, ItemSource.MAVEN, item));
    assertNull(result);
  }

  @Test
  public void store_mavenDir_acceptsMaven() throws Exception {
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    CacheProcess process = new CacheProcess(output, null, null, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/latte/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path result = process.publish(new FetchResult(artFile, ItemSource.MAVEN, item));
    assertNotNull(result);
    assertTrue(Files.isRegularFile(result));
  }

  @Test
  public void store_mavenDir_rejectsLatte() throws Exception {
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    CacheProcess process = new CacheProcess(output, null, null, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/latte/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path result = process.publish(new FetchResult(artFile, ItemSource.LATTE, item));
    assertNull(result);
  }

  @Test
  public void storeIntegration() throws Exception {
    Path cache = projectDir.resolve("build/test/deps");
    PathTools.prune(cache);

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:integration-build:integration-build:2.1.1-{integration}:jar", License.Licenses.get("ApacheV2_0"));

    Path artFile = projectDir.resolve("test-deps/integration/org/lattejava/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar");
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    Path file = process.publish(new FetchResult(artFile, ItemSource.LATTE, item));
    assertNotNull(file);
    assertTrue(file.toAbsolutePath().toString().replace('\\', '/').endsWith("build/test/deps/org/lattejava/test/integration-build/2.1.1-{integration}/integration-build-2.1.1-{integration}.jar"));
    assertTrue(Files.isRegularFile(file));
  }

  @Test
  public void fetch_withAlternative_primaryHit() {
    // When the primary item exists, alternatives should not be checked and the primary item name is returned
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactFile(),
        List.of(artifact.getArtifactAlternativeSourceFile()));
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertEquals(result.item().item, artifact.getArtifactFile());
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("multiple-versions-1.0.0.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void fetch_withAlternative_primaryMissAlternativeHit() {
    // When primary (-src.jar) doesn't exist but alternative (-sources.jar) does, the alternative is returned
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactSourceFile(),
        List.of(artifact.getArtifactAlternativeSourceFile()));
    FetchResult result = process.fetch(item, null);
    assertNotNull(result);
    assertEquals(result.item().item, artifact.getArtifactAlternativeSourceFile());
    assertTrue(result.file().toAbsolutePath().toString().replace('\\', '/').endsWith("multiple-versions-1.0.0-sources.jar"));
    assertTrue(Files.isRegularFile(result.file()));
  }

  @Test
  public void fetch_negativeCache() throws Exception {
    // When a .neg marker exists for the item, NegativeCacheException is thrown
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    Path negFile = projectDir.resolve("test-deps/latte/org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0-src.jar.neg");
    Files.createFile(negFile);
    try {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
          artifact.version.toString(), artifact.getArtifactSourceFile());
      process.fetch(item, null);
      fail("Expected NegativeCacheException");
    } catch (NegativeCacheException e) {
      // Expected
    } finally {
      Files.deleteIfExists(negFile);
    }
  }

  @Test
  public void fetch_negativeCache_mavenDir() throws Exception {
    // When a .neg marker exists in the Maven cache, NegativeCacheException is thrown
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    CacheProcess process = new CacheProcess(output, null, null, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    // Create the directory and .neg marker
    Path negFile = mavenCache.resolve("org/lattejava/test/multiple-versions/1.0.0/multiple-versions-1.0.0-src.jar.neg");
    Files.createDirectories(negFile.getParent());
    Files.createFile(negFile);
    try {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
          artifact.version.toString(), artifact.getArtifactSourceFile());
      process.fetch(item, null);
      fail("Expected NegativeCacheException");
    } catch (NegativeCacheException e) {
      // Expected
    } finally {
      PathTools.prune(mavenCache);
    }
  }

  @Test
  public void fetch_noNegativeCache() {
    // When no .neg marker exists and the item is missing, null is returned (not an exception)
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactSourceFile());
    FetchResult result = process.fetch(item, null);
    assertNull(result);
  }

  @Test
  public void fetch_noNegativeCache_mavenDir() throws Exception {
    // When no .neg marker exists in the Maven cache and the item is missing, null is returned
    Path mavenCache = projectDir.resolve("build/test/maven-deps");
    PathTools.prune(mavenCache);

    CacheProcess process = new CacheProcess(output, null, null, mavenCache.toString());
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactSourceFile());
    FetchResult result = process.fetch(item, null);
    assertNull(result);
  }

  @Test
  public void fetch_withAlternative_negativeCache() throws Exception {
    // When a .neg marker exists for the primary item, NegativeCacheException is thrown before checking alternatives
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.1.0:jar", License.Licenses.get("ApacheV2_0"));

    Path negFile = projectDir.resolve("test-deps/latte/org/lattejava/test/multiple-versions/1.1.0/multiple-versions-1.1.0-src.jar.neg");
    Files.deleteIfExists(negFile);
    Files.createDirectories(negFile.getParent());
    Files.createFile(negFile);
    try {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
          artifact.version.toString(), artifact.getArtifactSourceFile(),
          List.of(artifact.getArtifactAlternativeSourceFile()));
      process.fetch(item, null);
      fail("Expected NegativeCacheException");
    } catch (NegativeCacheException e) {
      // Expected
    } finally {
      Files.deleteIfExists(negFile);
    }
  }

  @Test
  public void fetch_withAlternative_noNegativeCache() {
    // When no .neg marker exists, primary is missing, and alternative is missing, null is returned
    CacheProcess process = new CacheProcess(output, projectDir.resolve("test-deps/latte").toString(), null, null);
    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), "nonexistent-1.0.0-src.jar",
        List.of("nonexistent-1.0.0-sources.jar"));
    FetchResult result = process.fetch(item, null);
    assertNull(result);
  }
}
