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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.domain.Version;
import org.lattejava.output.Output;

/**
 * This is an implementation of the Process that uses local caches to fetch and publish artifacts.
 * It manages up to three cache directories: one for Savant-sourced artifacts, one for integration-version
 * artifacts, and one for Maven-sourced artifacts. Any directory can be null to disable that cache.
 *
 * @author Brian Pontarelli
 */
public class CacheProcess implements Process {
  public final String integrationDir;

  public final String mavenDir;

  public final Output output;

  public final String savantDir;

  private record CacheHit(Path file, String matchedItem) {}

  public CacheProcess(Output output, String savantDir, String integrationDir, String mavenDir) {
    this.output = output;
    this.savantDir = savantDir;
    this.integrationDir = integrationDir;
    this.mavenDir = mavenDir;
  }

  /**
   * Checks the cache directories for the item. Tries the Savant cache first (tagging hits as SAVANT),
   * then the Maven cache (tagging hits as MAVEN). If found in either, the result is returned.
   * If not found in either, null is returned.
   *
   * @param item            The item being fetched.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return The FetchResult from the cache or null if it doesn't exist.
   * @throws NegativeCacheException If there is a negative cache record of the file, meaning it doesn't exist
   *     anywhere in the world.
   */
  @Override
  public FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws NegativeCacheException {
    // Try integration cache first for integration versions
    if (integrationDir != null && item.version.endsWith(Version.INTEGRATION)) {
      CacheHit hit = tryFetchCandidate(item, integrationDir);
      if (hit != null) {
        ResolvableItem matchedItem = hit.matchedItem.equals(item.item) ? item : new ResolvableItem(item, hit.matchedItem);
        return new FetchResult(hit.file, ItemSource.SAVANT, matchedItem);
      }
    }

    // Try Savant cache for non-integration versions
    if (savantDir != null) {
      CacheHit hit = tryFetchCandidate(item, savantDir);
      if (hit != null) {
        ResolvableItem matchedItem = hit.matchedItem.equals(item.item) ? item : new ResolvableItem(item, hit.matchedItem);
        return new FetchResult(hit.file, ItemSource.SAVANT, matchedItem);
      }
    }

    // Try Maven cache
    if (mavenDir != null) {
      CacheHit hit = tryFetchCandidate(item, mavenDir);
      if (hit != null) {
        ResolvableItem matchedItem = hit.matchedItem.equals(item.item) ? item : new ResolvableItem(item, hit.matchedItem);
        return new FetchResult(hit.file, ItemSource.MAVEN, matchedItem);
      }
    }

    return null;
  }

  /**
   * Publishes the given artifact item into the appropriate cache. Items are routed based on the
   * FetchResult's source: SAVANT items go to savantDir, MAVEN items go to mavenDir. Returns null
   * if the relevant directory is null or the source doesn't match either cache.
   *
   * @param fetchResult The fetch result containing the item, file, and source.
   * @return The path to the published file, or null if the source doesn't match.
   * @throws ProcessFailureException If the publish fails.
   */
  @Override
  public Path publish(FetchResult fetchResult) throws ProcessFailureException {
    String dir;
    ResolvableItem item = fetchResult.item();
    if (integrationDir != null && item.version.endsWith(Version.INTEGRATION)) {
      dir = integrationDir;
    } else if (fetchResult.source() == ItemSource.SAVANT) {
      dir = savantDir;
    } else if (fetchResult.source() == ItemSource.MAVEN) {
      dir = mavenDir;
    } else {
      return null;
    }

    if (dir == null) {
      return null;
    }
    Path itemFile = fetchResult.file();

    String cachePath = String.join("/", dir, item.group.replace('.', '/'), item.project, item.version, item.item);
    Path cacheFile = Paths.get(cachePath);
    if (Files.isDirectory(cacheFile)) {
      throw new ProcessFailureException("Your local artifact cache location is a directory [" + cacheFile.toAbsolutePath() + "]");
    }

    if (Files.isRegularFile(cacheFile)) {
      try {
        Files.delete(cacheFile);
      } catch (IOException e) {
        throw new ProcessFailureException("Unable to delete old file in the local cache to replace [" + cacheFile.toAbsolutePath() + "]", e);
      }
    } else if (!Files.exists(cacheFile)) {
      try {
        Files.createDirectories(cacheFile.getParent());
      } catch (IOException e) {
        throw new ProcessFailureException("Unable to create cache directory [" + cacheFile.getParent().toAbsolutePath() + "]");
      }
    }

    try {
      Files.copy(itemFile, cacheFile);
    } catch (IOException e) {
      // Clean up the artifact if it was a partial copy
      if (Files.exists(cacheFile)) {
        try {
          Files.delete(cacheFile);
        } catch (IOException e1) {
          // Smother since we are already in a failure state
        }
      }

      throw new ProcessFailureException(item, e);
    }

    output.debugln("Cached at [%s]", cacheFile);

    return cacheFile;
  }

  @Override
  public String toString() {
    return "Cache(savant=" + savantDir + ", integration=" + integrationDir + ", maven=" + mavenDir + ")";
  }

  private CacheHit tryFetchCandidate(ResolvableItem item, String cacheDir) {
    // Try primary item
    String path = String.join("/", cacheDir, item.group.replace('.', '/'), item.project, item.version, item.item);
    output.debugln("      - File [" + path + "]");
    Path file = Paths.get(path);
    if (Files.isRegularFile(file)) {
      output.debugln("      - Found");
      return new CacheHit(file, item.item);
    }

    // Check negative cache marker (only for primary item)
    Path negFile = Paths.get(path + ".neg");
    if (Files.isRegularFile(negFile)) {
      output.debugln("      - Found negative marker");
      throw new NegativeCacheException(item);
    }

    // Try alternative items
    for (String alt : item.alternativeItems) {
      String altPath = String.join("/", cacheDir, item.group.replace('.', '/'), item.project, item.version, alt);
      output.debugln("      - File [" + altPath + "] (alternative)");
      Path altFile = Paths.get(altPath);
      if (Files.isRegularFile(altFile)) {
        output.debugln("      - Found (alternative)");
        return new CacheHit(altFile, alt);
      }
    }

    output.debugln("      - Not found");
    return null;
  }
}
