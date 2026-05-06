/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import java.nio.file.Path;
import java.util.Objects;

/**
 * This class is a publishable artifact for a project. This is similar to an artifact, but doesn't have the group,
 * project and version, since those are controlled by the project and also has a file reference and dependencies
 * reference.
 *
 * @author Brian Pontarelli
 */
public class Publication {
  public final Artifact artifact;

  public final Path file;

  public final ArtifactMetaData metaData;

  public final Path sourceFile;

  public Publication(Artifact artifact, ArtifactMetaData metaData, Path file, Path sourceFile) {
    Objects.requireNonNull(artifact, "Publications must have an Artifact");
    Objects.requireNonNull(metaData, "Publications must have ArtifactMetaData");
    Objects.requireNonNull(file, "Publications must have a file");
    this.sourceFile = sourceFile;
    this.file = file;
    this.metaData = metaData;
    this.artifact = artifact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final Publication that = (Publication) o;
    return artifact.equals(that.artifact) && file.equals(that.file) && metaData.equals(that.metaData) &&
        (sourceFile != null ? sourceFile.equals(that.sourceFile) : that.sourceFile == null);
  }

  @Override
  public int hashCode() {
    int result = artifact.hashCode();
    result = 31 * result + metaData.hashCode();
    result = 31 * result + file.hashCode();
    result = 31 * result + (sourceFile != null ? sourceFile.hashCode() : 0);
    return result;
  }

  public String toString() {
    return artifact + "{licenses:" + metaData.licenses + "}{file:" + file + "}{source:" + (sourceFile != null ? sourceFile.toString() : "none") + "}";
  }
}
