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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.lattejava.domain.Version;

/**
 * This class defines a resolved artifact, which is an artifact after it has been downloaded as a dependency and is
 * fully resolved. This form of an artifact has a file (as a Path) to where the artifact is stored on the local disk.
 *
 * @author Brian Pontarelli
 */
public class ResolvedArtifact extends ReifiedArtifact {
  public final Path file;

  public final Path sourceFile;

  public ResolvedArtifact(ArtifactID id, Version version, List<License> licenses, Path file, Path sourceFile) {
    super(id, version, licenses);
    this.file = file;
    this.sourceFile = sourceFile;
  }

  public ResolvedArtifact(String spec, List<License> licenses, Path file, Path sourceFile) {
    super(spec, licenses);
    this.file = file;
    this.sourceFile = sourceFile;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final ResolvedArtifact that = (ResolvedArtifact) o;
    return Objects.equals(file, that.file);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (file != null ? file.hashCode() : 0);
    return result;
  }

  /**
   * @return This ResolvedArtifact as a Dependency. This is useful for comparing ResolvedArtifacts to
   *     {@link ReifiedArtifact}s.
   */
  public Artifact toArtifact() {
    return new Artifact(id, version);
  }
}
