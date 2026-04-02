/*
 * Copyright (c) 2014-2025, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep.workflow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.ArtifactTools;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.maven.MavenDependency;
import org.lattejava.dep.maven.MavenTools;
import org.lattejava.dep.maven.POM;
import org.lattejava.dep.workflow.process.FetchResult;
import org.lattejava.dep.workflow.process.ItemSource;
import org.lattejava.dep.workflow.process.NegativeCacheException;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.domain.Version;
import org.lattejava.domain.VersionException;
import org.lattejava.output.Output;
import org.lattejava.security.MD5Exception;


/**
 * This class models a grouping of a fetch and publish workflow.
 *
 * @author Brian Pontarelli
 */
public class Workflow {
  public final FetchWorkflow fetchWorkflow;

  public final Map<String, Version> mappings = new HashMap<>();

  public final Output output;

  public final PublishWorkflow publishWorkflow;

  public final Map<String, String> rangeMappings = new HashMap<>();

  private final Map<String, POM> pomCache = new HashMap<>();

  public Workflow(FetchWorkflow fetchWorkflow, PublishWorkflow publishWorkflow, Output output) {
    this.fetchWorkflow = fetchWorkflow;
    this.publishWorkflow = publishWorkflow;
    this.output = output;
  }

  /**
   * Fetches the artifact itself. Every artifact in a Latte dependency graph is required to exist. Therefore, Latte
   * never negative caches artifact files and this method will always return the artifact file or throw an
   * ArtifactMissingException.
   *
   * @param artifact The artifact to fetch.
   * @return The Path of the artifact and never null.
   * @throws ArtifactMissingException If the artifact could not be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the
   *     artifact.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public Path fetchArtifact(Artifact artifact) throws ArtifactMissingException, ProcessFailureException, MD5Exception {
    // Try the non-semantic version first since it is the real version on disk and in remote repositories
    FetchResult result = null;
    if (artifact.nonSemanticVersion != null) {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.nonSemanticVersion, artifact.getArtifactNonSemanticFile());
      result = fetchWorkflow.fetchItem(item, publishWorkflow);
    }

    // Fall back to the semantic version
    if (result == null) {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
      result = fetchWorkflow.fetchItem(item, publishWorkflow);
    }

    if (result == null) {
      throw new ArtifactMissingException(artifact);
    }

    return result.file();
  }

  /**
   * Fetches the artifact metadata. Every artifact in Latte is required to have an AMD file. Otherwise, it is
   * considered a missing artifact entirely. Therefore, Latte never negative caches AMD files and this method will
   * always return an AMD file or throw an ArtifactMetaDataMissingException.
   *
   * @param artifact The artifact to fetch the metadata for.
   * @return The ArtifactMetaData object and never null.
   * @throws ArtifactMetaDataMissingException If the AMD file could not be found.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the AMD
   *     file.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public ArtifactMetaData fetchMetaData(Artifact artifact) throws ArtifactMetaDataMissingException, ProcessFailureException, MD5Exception {
    // Defined here in case an exception is thrown in the catch block below
    ResolvableItem item = new ResolvableItem(
        artifact.id.group, artifact.id.project, artifact.id.name,
        artifact.version.toString(), artifact.getArtifactMetaDataFile(),
        List.of(artifact.getArtifactPOMFile())
    );

    // Try the non-semantic version first since the POM lives under the real version directory on disk
    // (AMD files don't exist under non-semantic version directories since AMD is a Latte concept)
    try {
      if (artifact.nonSemanticVersion != null) {
        ResolvableItem nonSemanticItem = new ResolvableItem(
            artifact.id.group, artifact.id.project, artifact.id.project,
            artifact.nonSemanticVersion, artifact.getArtifactNonSemanticPOMFile()
        );
        FetchResult result = fetchWorkflow.fetchItem(nonSemanticItem, publishWorkflow);
        if (result != null) {
          try {
            POM pom = loadPOM(artifact, result.file());
            return translatePOM(pom);
          } catch (Exception e) {
            // POM processing failed (e.g. range dependencies without mappings, missing imports).
            // Fall through to semantic AMD/POM lookup which may have an AMD file without these issues.
            output.debugln("Non-semantic POM processing failed for [%s], falling back to semantic lookup: %s", artifact, e.getMessage());
          }
        }
      }

      // Fall back to semantic version — try AMD (primary) with POM as alternative
      FetchResult result = fetchWorkflow.fetchItem(item, publishWorkflow);
      if (result != null) {
        if (result.item().item.endsWith(".amd")) {
          return ArtifactTools.parseArtifactMetaData(result.file(), mappings);
        } else {
          // POM was found as alternative — process it through the POM pipeline
          POM pom = loadPOM(artifact, result.file());
          return translatePOM(pom);
        }
      }

      // Neither AMD nor POM found — try loadPOM directly as last resort
      POM pom = loadPOM(artifact);
      if (pom != null) {
        return translatePOM(pom);
      }

      throw new ArtifactMetaDataMissingException(artifact);
    } catch (IllegalArgumentException | NullPointerException | IOException | VersionException e) {
      throw new ProcessFailureException(item, e);
    }
  }

  /**
   * Fetches the source of the artifact. If a source file is missing, this method stores a negative file in the cache so
   * that an attempt to download the source file isn't made each time. This is required so that offline work can be done
   * by only hitting the local cache of dependencies.
   *
   * @param artifact The artifact to fetch the source for.
   * @return The Path of the source or null if it doesn't exist.
   * @throws ProcessFailureException If any of the processes encountered a failure while attempting to fetch the
   *     source file.
   * @throws MD5Exception If the item's MD5 file did not match the item.
   */
  public Path fetchSource(Artifact artifact) throws ProcessFailureException, MD5Exception {
    try {
      // Try non-semantic version first (-sources.jar with original version) since it is the real version on disk
      FetchResult result = null;
      if (artifact.nonSemanticVersion != null) {
        ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
            artifact.nonSemanticVersion, artifact.getArtifactNonSemanticAlternativeSourceFile());
        result = fetchWorkflow.fetchItem(item, publishWorkflow);
      }

      // Fall back to Latte-style source (-src.jar) with Maven-style alternative (-sources.jar)
      if (result == null) {
        ResolvableItem item = new ResolvableItem(
            artifact.id.group, artifact.id.project, artifact.id.name,
            artifact.version.toString(), artifact.getArtifactSourceFile(),
            List.of(artifact.getArtifactAlternativeSourceFile())
        );
        result = fetchWorkflow.fetchItem(item, publishWorkflow);
      }

      // Negative cache if not found
      if (result == null) {
        ResolvableItem negItem = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name,
            artifact.version.toString(), artifact.getArtifactSourceFile());
        publishWorkflow.publishNegative(negItem, ItemSource.LATTE);
      }

      return result != null ? result.file() : null;
    } catch (NegativeCacheException e) {
      // This is a short-circuit exit from the workflow. It is only thrown by the CacheProcess and indicates that the
      // search for the source JAR should stop immediately.
      return null;
    }
  }

  private POM loadPOM(Artifact artifact) {
    String cacheKey = artifact.id.group + ":" + artifact.id.project + ":" + artifact.version;
    POM cached = pomCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }

    Path file = fetchPOMFile(artifact);
    if (file == null) {
      return null;
    }

    return processPOM(cacheKey, file);
  }

  /**
   * Called from fetchMetaData when POM was found as an alternative to AMD.
   */
  private POM loadPOM(Artifact artifact, Path preloadedFile) {
    String cacheKey = artifact.id.group + ":" + artifact.id.project + ":" + artifact.version;
    POM cached = pomCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    return processPOM(cacheKey, preloadedFile);
  }

  private Path fetchPOMFile(Artifact artifact) {
    // Maven doesn't use artifact names (via classifiers) when resolving POMs. Therefore, we need to use the project id twice for the item
    // Try the non-semantic version first since it is the real version on disk and in remote repositories
    FetchResult result = null;
    if (artifact.nonSemanticVersion != null) {
      output.debugln("[Looking for POM using non-semantic version]");
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.project, artifact.nonSemanticVersion, artifact.getArtifactNonSemanticPOMFile());
      result = fetchWorkflow.fetchItem(item, publishWorkflow);
    }

    // Fall back to the semantic version
    if (result == null) {
      ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.project, artifact.version.toString(), artifact.getArtifactPOMFile());
      result = fetchWorkflow.fetchItem(item, publishWorkflow);
    }

    return result != null ? result.file() : null;
  }

  private POM processPOM(String cacheKey, Path file) {
    POM pom = MavenTools.parsePOM(file, output);
    pom.replaceKnownVariablesAndFillInDependencies();
    pom.replaceRangeValuesWithMappings(rangeMappings);

    // Recursively load the parent POM and any dependencies that are `import`
    if (pom.parentGroup != null && pom.parentId != null && pom.parentVersion != null) {
      POM parent = new POM(pom.parentGroup, pom.parentId, pom.parentVersion);
      Artifact parentPOM = MavenTools.toArtifact(parent, "pom", mappings);
      pom.parent = loadPOM(parentPOM);
    }

    // Now that we have the parents (recursively), load all the variables and fix top-level POM definitions
    pom.replaceKnownVariablesAndFillInDependencies();
    if (pom.group == null) {
      pom.group = pom.parent.group;
    }
    if (pom.version == null) {
      pom.version = pom.parent.version;
    }

    // Load the imports in the POM until there are no more imports. Each iteration needs to fill in variables
    List<MavenDependency> imports = pom.imports();
    while (!imports.isEmpty()) {
      for (MavenDependency anImport : imports) {
        Artifact dep = MavenTools.toArtifact(anImport, "pom", mappings);
        POM importPOM = loadPOM(dep);
        if (importPOM == null) {
          throw new ProcessFailureException("Unable to import Maven dependency definitions into a POM because the referenced import could not be found. [" + dep + "]");
        }

        pom.removeDependencyDefinition(anImport);
        pom.dependenciesDefinitions.addAll(importPOM.dependenciesDefinitions);
      }

      imports = pom.imports();
    }

    // Fill in variables again in case there are new ones. This also fills out any missing dependencies
    pom.replaceKnownVariablesAndFillInDependencies();
    pom.replaceRangeValuesWithMappings(rangeMappings);

    pomCache.put(cacheKey, pom);
    return pom;
  }

  private ArtifactMetaData translatePOM(POM pom) {
    return new ArtifactMetaData(MavenTools.toLatteDependencies(pom, mappings), MavenTools.toLatteLicenses(pom));
  }
}
