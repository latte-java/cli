/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
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
