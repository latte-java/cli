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
package org.lattejava.dep.domain;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class models a set of dependencies on Dependency (artifacts) objects broken into DependencyGroups.
 *
 * @author Brian Pontarelli
 */
public class Dependencies {
  public final Map<String, DependencyGroup> groups = new LinkedHashMap<>();

  public Dependencies(DependencyGroup... groups) {
    for (DependencyGroup group : groups) {
      this.groups.put(group.name, group);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Dependencies that = (Dependencies) o;
    return groups.equals(that.groups);
  }

  /**
   * Collects all the artifacts from all the groups.
   *
   * @return All the artifacts.
   */
  public Set<Artifact> getAllArtifacts() {
    Set<Artifact> set = new HashSet<>();
    groups.values().forEach((group) -> set.addAll(group.dependencies));
    return set;
  }

  @Override
  public int hashCode() {
    return groups.hashCode();
  }

  @Override
  public String toString() {
    return "{\n\t" + groups.values().stream().map(DependencyGroup::toString).collect(Collectors.joining("\n")) + "}\n";
  }
}
