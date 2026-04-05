/*
 * Copyright (c) 2024, Inversoft Inc., All Rights Reserved
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.ArtifactSpec;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.json.AMDJson;
import org.lattejava.dep.domain.json.AMDJsonDependency;
import org.lattejava.dep.domain.json.AMDJsonLicense;
import org.lattejava.domain.Version;
import org.lattejava.domain.VersionException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class is a toolkit for handling artifact operations.
 *
 * @author Brian Pontarelli
 */
public class ArtifactTools {
  /**
   * Maven version error.
   */
  public static final String VersionError = """
      Invalid Version in the dependency graph from a Maven dependency [%s]. You must specify a semantic version mapping for Latte to properly handle Maven dependencies. This goes at the top-level of the project file and looks like this:

      project(...) {
        workflow {
          semanticVersions {
            mapping(id: "org.badver:badver:1.0.0.Final", version: "1.0.0")
          }
        }
      }""";

  /**
   * Determines the semantic version of an artifact based on the original version from the specification, which might be
   * a Maven version.
   *
   * @param spec     The specification.
   * @param mappings The version mappings from non-semantic to semantic.
   * @return The version and never null.
   * @throws VersionException If the version is non-semantic and there is no mapping.
   */
  public static Version determineSemanticVersion(ArtifactSpec spec, Map<String, Version> mappings)
      throws VersionException {
    Version version = mappings.get(spec.mavenSpec);
    if (version != null) {
      return version; // Always favor a mapping
    }

    String originalVersion = spec.version;
    try {
      return new Version(originalVersion);
    } catch (VersionException e) {
      // If the version is janky (i.e. it contains random characters), throw an exception
      if (originalVersion.chars().anyMatch(ch -> !Character.isDigit(ch) && ch != '.')) {
        throw new VersionException(String.format(VersionError, spec.mavenSpec));
      }

      // Otherwise, try again by "fixing" the Maven version
      int dots = (int) originalVersion.chars().filter(ch -> ch == '.').count();
      if (dots == 0) {
        originalVersion += ".0.0";
      } else if (dots == 1) {
        originalVersion += ".0";
      }

      try {
        return new Version(originalVersion);
      } catch (VersionException e2) {
        throw new VersionException(String.format(VersionError, spec.mavenSpec));
      }
    }
  }

  /**
   * Parses the MetaData from the given JSON `.amd` file.
   *
   * @param file     The file to read the JSON MetaData information from.
   * @param mappings The semantic version mappings used when the JSON contains non-semantic versions.
   * @return The MetaData parsed.
   * @throws IOException If the parse operation failed because of an IO error.
   * @throws VersionException If any of the version strings could not be parsed.
   */
  public static ArtifactMetaData parseArtifactMetaData(Path file, Map<String, Version> mappings)
      throws IOException, VersionException {
    ObjectMapper objectMapper = new ObjectMapper();
    AMDJson amdJson = objectMapper.readValue(file.toFile(), AMDJson.class);

    // Convert licenses
    List<License> licenses = new ArrayList<>();
    if (amdJson.licenses != null) {
      for (AMDJsonLicense jsonLicense : amdJson.licenses) {
        String text = jsonLicense.text;
        if (text != null) {
          text = text.trim();
          if (text.isEmpty()) {
            text = null;
          }
        }
        licenses.add(License.parse(jsonLicense.type, text));
      }
    }

    // Convert dependency groups
    Dependencies dependencies = null;
    if (amdJson.dependencyGroups != null) {
      dependencies = new Dependencies();
      for (Map.Entry<String, List<AMDJsonDependency>> entry : amdJson.dependencyGroups.entrySet()) {
        DependencyGroup group = new DependencyGroup(entry.getKey(), true);
        for (AMDJsonDependency jsonDep : entry.getValue()) {
          ArtifactSpec spec = new ArtifactSpec(jsonDep.id);
          List<ArtifactID> exclusions = new ArrayList<>();
          if (jsonDep.exclusions != null) {
            for (String exclusionSpec : jsonDep.exclusions) {
              exclusions.add(new ArtifactID(exclusionSpec));
            }
          }

          Version version;
          String nonSemanticVersion = null;
          if (!isStrictSemanticVersion(spec.version)) {
            nonSemanticVersion = spec.version;
            version = determineSemanticVersion(spec, mappings);
          } else {
            try {
              version = new Version(spec.version);
            } catch (VersionException e) {
              nonSemanticVersion = spec.version;
              version = determineSemanticVersion(spec, mappings);
            }
          }

          group.dependencies.add(new Artifact(spec.id, version, nonSemanticVersion, exclusions));
        }
        dependencies.groups.put(entry.getKey(), group);
      }
    }

    return new ArtifactMetaData(dependencies, licenses);
  }

  /**
   * Generates a temporary file that contains ArtifactMetaData JSON.
   *
   * @param artifactMetaData The MetaData object to serialize to JSON.
   * @return The temp file and never null.
   * @throws IOException If the temp could not be created or the JSON could not be written.
   */
  public static Path generate(ArtifactMetaData artifactMetaData) throws IOException {
    File tmp = File.createTempFile("latte", "amd.json");
    tmp.deleteOnExit();

    List<AMDJsonLicense> licenses = new ArrayList<>();
    for (License license : artifactMetaData.licenses) {
      licenses.add(new AMDJsonLicense(license.identifier, license.customText ? license.text : null));
    }

    Map<String, List<AMDJsonDependency>> dependencyGroups = null;
    Dependencies dependencies = artifactMetaData.dependencies;
    if (dependencies != null) {
      dependencyGroups = new java.util.LinkedHashMap<>();
      for (Map.Entry<String, DependencyGroup> entry : dependencies.groups.entrySet()) {
        DependencyGroup group = entry.getValue();
        if (!group.export) {
          continue;
        }

        List<AMDJsonDependency> jsonDeps = new ArrayList<>();
        for (Artifact artifact : group.dependencies) {
          String version = artifact.nonSemanticVersion != null ? artifact.nonSemanticVersion : artifact.version.toString();
          String id = artifact.id.group + ":" + artifact.id.project + ":" + artifact.id.name + ":" + version + ":" + artifact.id.type;
          List<String> exclusions = null;
          if (!artifact.exclusions.isEmpty()) {
            exclusions = new ArrayList<>();
            for (ArtifactID exclusion : artifact.exclusions) {
              exclusions.add(exclusion.group + ":" + exclusion.project + ":" + exclusion.name + ":" + exclusion.type);
            }
          }
          jsonDeps.add(new AMDJsonDependency(id, exclusions));
        }
        dependencyGroups.put(entry.getKey(), jsonDeps);
      }
    }

    AMDJson amdJson = new AMDJson(licenses, dependencyGroups);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp, amdJson);

    return tmp.toPath();
  }

  /**
   * Returns true if the version string is a strict semantic version (has at least major.minor.patch, i.e. at least 2
   * dots before any pre-release or metadata separator).
   *
   * @param version The version string to check.
   * @return True if the version has at least two dots before any - or + character.
   */
  private static boolean isStrictSemanticVersion(String version) {
    int dots = 0;
    for (int i = 0; i < version.length(); i++) {
      char c = version.charAt(i);
      if (c == '-' || c == '+') {
        break;
      }
      if (c == '.') {
        dots++;
      }
    }
    return dots >= 2;
  }

}
