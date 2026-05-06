/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class defines a group of artifacts that the project depends on.
 *
 * @author Brian Pontarelli
 */
public class DependencyGroup {
  public final List<Artifact> dependencies = new ArrayList<>();

  public final boolean export;

  public final String name;

  /**
   * Constructs a Dependency group.
   *
   * @param name         The name of the group (compile, run, etc).
   * @param export       Whether this group is exported or not.
   * @param dependencies The initial dependencies of the group.
   * @throws NullPointerException If the type parameter is null.
   */
  public DependencyGroup(String name, boolean export, Artifact... dependencies) throws NullPointerException {
    Objects.requireNonNull(name, "DependencyGroups must have a type specified (i.e. compile, run, test, etc.)");
    this.name = name;
    this.export = export;
    Collections.addAll(this.dependencies, dependencies);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final DependencyGroup that = (DependencyGroup) o;
    return export == that.export && dependencies.equals(that.dependencies) && name.equals(that.name);
  }

  @Override
  public int hashCode() {
    int result = dependencies.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + (export ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return name + ": [\n\t\t" + dependencies.stream().map(Artifact::toString).collect(Collectors.joining("\n\t\t")) + "\n\t]\n";
  }
}
