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
