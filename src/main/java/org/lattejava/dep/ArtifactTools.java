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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.ArtifactSpec;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.domain.Version;
import org.lattejava.domain.VersionException;

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
   * @throws IOException      If the parse operation failed because of an IO error.
   * @throws VersionException If any of the version strings could not be parsed.
   */
  public static ArtifactMetaData parseArtifactMetaData(Path file, Map<String, Version> mappings)
      throws IOException, VersionException {
    JSONParser parser = new JSONParser();
    JSONObject root;
    try (FileReader reader = new FileReader(file.toFile())) {
      root = (JSONObject) parser.parse(reader);
    } catch (ParseException e) {
      throw new IOException("Failed to parse AMD JSON file: " + file, e);
    }

    // Convert licenses
    List<License> licenses = new ArrayList<>();
    JSONArray licensesArray = (JSONArray) root.get("licenses");
    if (licensesArray != null) {
      for (Object obj : licensesArray) {
        JSONObject jsonLicense = (JSONObject) obj;
        String type = (String) jsonLicense.get("type");
        String text = (String) jsonLicense.get("text");
        if (text != null) {
          text = text.trim();
          if (text.isEmpty()) {
            text = null;
          }
        }
        licenses.add(License.parse(type, text));
      }
    }

    // Convert dependency groups
    Dependencies dependencies = null;
    JSONObject depGroupsObj = (JSONObject) root.get("dependencyGroups");
    if (depGroupsObj != null) {
      dependencies = new Dependencies();
      for (Object key : depGroupsObj.keySet()) {
        String groupName = (String) key;
        JSONArray depsArray = (JSONArray) depGroupsObj.get(groupName);
        DependencyGroup group = new DependencyGroup(groupName, true);
        for (Object depObj : depsArray) {
          JSONObject jsonDep = (JSONObject) depObj;
          String id = (String) jsonDep.get("id");
          ArtifactSpec spec = new ArtifactSpec(id);
          List<ArtifactID> exclusions = new ArrayList<>();
          JSONArray exclusionsArray = (JSONArray) jsonDep.get("exclusions");
          if (exclusionsArray != null) {
            for (Object exObj : exclusionsArray) {
              exclusions.add(new ArtifactID((String) exObj));
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
        dependencies.groups.put(groupName, group);
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

    JSONObject root = new JSONObject();
    JSONArray licensesArray = new JSONArray();
    for (License license : artifactMetaData.licenses) {
      JSONObject licObj = new JSONObject();
      licObj.put("type", license.identifier);
      if (license.customText && license.text != null) {
        licObj.put("text", license.text);
      }
      licensesArray.add(licObj);
    }
    root.put("licenses", licensesArray);

    Dependencies dependencies = artifactMetaData.dependencies;
    if (dependencies != null) {
      JSONObject depGroupsObj = new JSONObject();
      for (Map.Entry<String, DependencyGroup> entry : dependencies.groups.entrySet()) {
        DependencyGroup group = entry.getValue();
        if (!group.export) {
          continue;
        }

        JSONArray depsArray = new JSONArray();
        for (Artifact artifact : group.dependencies) {
          JSONObject depObj = new JSONObject();
          String version = artifact.nonSemanticVersion != null ? artifact.nonSemanticVersion : artifact.version.toString();
          String id = artifact.id.group + ":" + artifact.id.project + ":" + artifact.id.name + ":" + version + ":" + artifact.id.type;
          depObj.put("id", id);
          if (!artifact.exclusions.isEmpty()) {
            JSONArray exArray = new JSONArray();
            for (ArtifactID exclusion : artifact.exclusions) {
              exArray.add(exclusion.group + ":" + exclusion.project + ":" + exclusion.name + ":" + exclusion.type);
            }
            depObj.put("exclusions", exArray);
          }
          depsArray.add(depObj);
        }
        depGroupsObj.put(entry.getKey(), depsArray);
      }
      root.put("dependencyGroups", depGroupsObj);
    }

    try (FileWriter writer = new FileWriter(tmp)) {
      root.writeJSONString(writer);
    }

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
