/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import java.util.Arrays;
import java.util.List;

/**
 * This class is the model for the artifact metadata XML file that is published along with artifacts.
 *
 * @author Brian Pontarelli
 */
public class ArtifactMetaData {
  public final Dependencies dependencies;

  public final List<License> licenses;

  public ArtifactMetaData(Dependencies dependencies, License... licenses) {
    this(dependencies, Arrays.asList(licenses));
  }

  public ArtifactMetaData(Dependencies dependencies, List<License> licenses) {
    this.dependencies = dependencies;
    this.licenses = licenses;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ArtifactMetaData that = (ArtifactMetaData) o;
    return dependencies.equals(that.dependencies) && licenses.equals(that.licenses);
  }

  @Override
  public int hashCode() {
    int result = dependencies.hashCode();
    result = 31 * result + licenses.hashCode();
    return result;
  }
}
