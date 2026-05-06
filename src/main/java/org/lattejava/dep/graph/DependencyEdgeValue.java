/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.graph;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.lattejava.dep.domain.License;
import org.lattejava.domain.Version;

/**
 * This class stores the information for edges between artifacts in the graph.
 *
 * @author Brian Pontarelli
 */
public class DependencyEdgeValue {
  public final String dependencyNonSemanticVersion;

  public final Version dependencyVersion;

  public final String dependentNonSemanticVersion;

  public final Version dependentVersion;

  public final List<License> licenses;

  public final String type;

  public DependencyEdgeValue(Version dependentVersion, Version dependencyVersion, String type, License... licenses) {
    this(dependentVersion, null, dependencyVersion, null, type, Arrays.asList(licenses));
  }

  public DependencyEdgeValue(Version dependentVersion, String dependentNonSemanticVersion, Version dependencyVersion,
                             String dependencyNonSemanticVersion, String type, List<License> licenses) {
    Objects.requireNonNull(dependentVersion, "DependencyEdgeValue requires a dependentVersion");
    Objects.requireNonNull(dependencyVersion, "DependencyEdgeValue requires a dependencyVersion");
    Objects.requireNonNull(type, "DependencyEdgeValue requires a type");
    Objects.requireNonNull(licenses, "DependencyEdgeValue requires a license");
    this.dependentVersion = dependentVersion;
    this.dependentNonSemanticVersion = dependentNonSemanticVersion;
    this.dependencyVersion = dependencyVersion;
    this.dependencyNonSemanticVersion = dependencyNonSemanticVersion;
    this.type = type;
    this.licenses = List.copyOf(licenses);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final DependencyEdgeValue that = (DependencyEdgeValue) o;
    return dependencyVersion.equals(that.dependencyVersion) &&
        dependentVersion.equals(that.dependentVersion) &&
        type.equals(that.type) &&
        licenses.equals(that.licenses);
  }

  @Override
  public int hashCode() {
    int result = dependencyVersion.hashCode();
    result = 31 * result + dependentVersion.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }

  public String toString() {
    return dependentVersion + " ---(type=" + type + ",licenses=" + licenses + ")--> " + dependencyVersion;
  }
}
