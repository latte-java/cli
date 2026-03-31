/*
 * Copyright (c) 2013-2025, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The necessary information from the POM.
 *
 * @author Brian Pontarelli
 */
public class POM {
  public List<MavenDependency> dependencies = new ArrayList<>();

  public List<MavenDependency> dependenciesDefinitions = new ArrayList<>();

  public String group;

  public String id;

  public List<MavenLicense> licenses = new ArrayList<>();

  public String name;

  public String packaging;

  public POM parent;

  public String parentGroup;

  public String parentId;

  public String parentVersion;

  public Map<String, String> properties = new HashMap<>();

  public String version;

  public POM() {
  }

  public POM(String group, String id, String version) {
    this.group = group;
    this.id = id;
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof POM)) return false;
    final POM pom = (POM) o;
    return Objects.equals(id, pom.id) && Objects.equals(group, pom.group) && Objects.equals(version, pom.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, group, version);
  }

  public List<MavenDependency> imports() {
    return dependenciesDefinitions.stream()
                                  .filter(def -> def.scope.equalsIgnoreCase("import"))
                                  .collect(Collectors.toList());
  }

  public void removeDependencyDefinition(MavenDependency def) {
    dependenciesDefinitions.remove(def);
  }

  public void replaceKnownVariablesAndFillInDependencies() {
    Map<String, String> allProperties = resolveAllProperties();
    group = MavenTools.replaceProperties(group, allProperties);
    id = MavenTools.replaceProperties(id, allProperties);
    version = MavenTools.replaceProperties(version, allProperties);
    parentGroup = MavenTools.replaceProperties(parentGroup, allProperties);
    parentId = MavenTools.replaceProperties(parentId, allProperties);
    parentVersion = MavenTools.replaceProperties(parentVersion, allProperties);

    for (MavenDependency def : dependenciesDefinitions) {
      fillInDependency(def, allProperties);
    }

    for (MavenDependency dep : dependencies) {
      fillInDependency(dep, allProperties);
    }
  }

  public void replaceRangeValuesWithMappings(Map<String, String> rangeMappings) {
    for (MavenDependency dep : dependencies) {
      mapConcreteRanges(dep, rangeMappings);
    }

    for (MavenDependency dep : dependenciesDefinitions) {
      mapConcreteRanges(dep, rangeMappings);
    }
  }

  public List<MavenDependency> resolveAllDependencies() {
    List<MavenDependency> allDeps = new ArrayList<>(dependencies);
    POM current = parent;
    while (current != null) {
      allDeps.addAll(current.dependencies);
      current = current.parent;
    }

    return allDeps;
  }

  public List<MavenDependency> resolveAllDependencyDefinitions() {
    List<MavenDependency> allDefinitions = new ArrayList<>(dependenciesDefinitions);
    POM current = parent;
    while (current != null) {
      allDefinitions.addAll(current.dependenciesDefinitions);
      current = current.parent;
    }

    return allDefinitions;
  }

  public Map<String, String> resolveAllProperties() {
    Map<String, String> allProperties = new HashMap<>();
    POM current = this;
    while (current != null) {
      current.properties.forEach(allProperties::putIfAbsent);
      current.properties.forEach((key, value) -> allProperties.putIfAbsent("parent." + key, value));
      current.properties.forEach((key, value) -> allProperties.putIfAbsent("project.parent." + key, value));

      if (current.version != null) {
        allProperties.putIfAbsent("project.version", current.version);
        // 'pom' and no prefix are deprecated in favor of 'project' but they still exist in the wild.
        allProperties.putIfAbsent("pom.version", current.properties.get("project.version"));
        allProperties.putIfAbsent("version", current.properties.get("project.version"));
      }

      if (current.group != null) {
        allProperties.putIfAbsent("project.groupId", current.group);
        // 'pom' and no prefix are deprecated in favor of 'project' but they still exist in the wild.
        allProperties.putIfAbsent("pom.groupId", current.group);
        allProperties.putIfAbsent("groupId", current.group);
      }

      if (current.id != null) {
        allProperties.putIfAbsent("project.artifactId", current.id);
        // 'pom' and no prefix are deprecated in favor of 'project' but they still exist in the wild.
        allProperties.putIfAbsent("pom.artifactId", current.id);
        allProperties.putIfAbsent("artifactId", current.id);
      }

      if (current.name != null) {
        allProperties.putIfAbsent("project.name", current.name);
      }

      if (current.packaging != null) {
        allProperties.putIfAbsent("project.packaging", current.packaging);
      }

      current = current.parent;
    }

    return allProperties;
  }

  public String toSpecification() {
    return group + ":" + id + ":" + version;
  }

  public String toString() {
    return group + ":" + id + ":" + version;
  }

  private void fillInDependency(MavenDependency dep, Map<String, String> allProperties) {
    dep.group = MavenTools.replaceProperties(dep.group, allProperties);
    dep.id = MavenTools.replaceProperties(dep.id, allProperties);
    dep.type = MavenTools.replaceProperties(dep.type, allProperties);
    dep.scope = MavenTools.replaceProperties(dep.scope, allProperties);
    dep.version = MavenTools.replaceProperties(dep.version, allProperties);
    dep.classifier = MavenTools.replaceProperties(dep.classifier, allProperties);

    List<MavenDependency> allDefinitions = resolveAllDependencyDefinitions();
    if (dep.optional == null) {
      dep.optional = allDefinitions.stream()
                                   .filter(def -> def.group.equals(dep.group) && def.id.equals(dep.id))
                                   .findFirst()
                                   .map(def -> def.optional)
                                   .orElse(null);
    }
    dep.optional = MavenTools.replaceProperties(dep.optional, allProperties);

    if (dep.scope == null) {
      dep.scope = allDefinitions.stream()
                                .filter(def -> def.group.equals(dep.group) && def.id.equals(dep.id))
                                .findFirst()
                                .map(def -> def.scope)
                                .orElse(null);
    }
    dep.scope = MavenTools.replaceProperties(dep.scope, allProperties);
    if (dep.scope == null) {
      dep.scope = "compile";
    }

    if (dep.version == null) {
      dep.version = allDefinitions.stream()
                                  .filter(def -> def.group.equals(dep.group) && def.id.equals(dep.id))
                                  .findFirst()
                                  .map(def -> def.version)
                                  .orElse(null);
    }
    dep.version = MavenTools.replaceProperties(dep.version, allProperties);
  }

  private void mapConcreteRanges(MavenDependency dep, Map<String, String> rangeMappings) {
    if (dep.version == null) {
      return;
    }

    boolean isRange = dep.version.startsWith("[") || dep.version.startsWith("(");
    if (isRange) {
      // Handle hard requirement, not sure why anyone would do this?
      if (dep.version.startsWith("[") && dep.version.endsWith("]") && !dep.version.contains(",")) {
        dep.version = dep.version.substring(1, dep.version.length() - 1);
        return;
      }

      // Apart from the above case, a mapping is required.
      String concreteMavenVersion = rangeMappings.get(dep.toSpecification());
      if (concreteMavenVersion == null) {
        throw new VersionRangeException("""
            Fortunately, version ranges are not supported. You must provide a rangeMapping in the build file.
            
            Example:
            
            semanticVersions {
              rangeMapping(id: "{spec}", version: "{version}")
            }
            
            Where {version} is the concrete version within the range that you wish to utilize from the external maven repository.
            Note that this version should be the exact version as it is found in Maven. If the version is not semantic, it is possible
            that you may also need to provide a version mapping as well.
            """.replace("{spec}", dep.toSpecification()));
      }

      dep.version = concreteMavenVersion;
    }
  }
}
