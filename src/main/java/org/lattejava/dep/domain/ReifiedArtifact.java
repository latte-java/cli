/*
 * Copyright (c) 2014-2024, Inversoft Inc., All Rights Reserved
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.lattejava.domain.Version;

/**
 * This class defines an artifact that has been completely built by the dependency process. Usually, a Dependency (which
 * is an abstract artifact specialization) has the AMD file downloaded. That file contains additional information that
 * the dependent project doesn't know about the artifact. The information in the AMD file combined with the information
 * from the Dependency results in this class.
 *
 * @author Brian Pontarelli
 */
public class ReifiedArtifact extends Artifact {
  public final List<License> licenses;

  public ReifiedArtifact(ArtifactID id, Version version, License... licenses) {
    this(id, version, null, Collections.emptyList(), Arrays.asList(licenses));
  }

  public ReifiedArtifact(ArtifactID id, Version version, List<License> licenses) {
    this(id, version, null, Collections.emptyList(), licenses);
  }

  public ReifiedArtifact(ArtifactID id, Version version, List<ArtifactID> exclusions, List<License> licenses) {
    this(id, version, null, exclusions, licenses);
  }

  public ReifiedArtifact(ArtifactID id, Version version, String nonSemanticVersion, List<License> licenses) {
    this(id, version, nonSemanticVersion, Collections.emptyList(), licenses);
  }

  public ReifiedArtifact(ArtifactID id, Version version, String nonSemanticVersion, List<ArtifactID> exclusions, List<License> licenses) {
    super(id, version, nonSemanticVersion, exclusions);
    Objects.requireNonNull(licenses, "Artifacts must have a license");
    this.licenses = licenses;
  }

  /**
   * See {@link Artifact#Artifact(String)} for what is allowed for the specification String.
   *
   * @param spec     The specification String.
   * @param licenses The licenses.
   */
  public ReifiedArtifact(String spec, License... licenses) {
    this(spec, Arrays.asList(licenses));
  }

  /**
   * See {@link Artifact#Artifact(String)} for what is allowed for the specification String.
   *
   * @param spec     The specification String.
   * @param licenses The licenses.
   */
  public ReifiedArtifact(String spec, List<License> licenses) {
    this(spec, null, false, Collections.emptyList(), licenses);
  }

  /**
   * See {@link Artifact#Artifact(String)} for what is allowed for the specification String.
   *
   * @param spec                   The specification String.
   * @param nonSemanticVersion     The non-sematic version for the super constructor.
   * @param skipCompatibilityCheck The skip compatibility check flag for the super constructor.
   * @param exclusions             The list of exclusions for the super constructor.
   * @param licenses               The licenses.
   */
  public ReifiedArtifact(String spec, String nonSemanticVersion, boolean skipCompatibilityCheck, List<ArtifactID> exclusions, List<License> licenses) {
    super(spec, nonSemanticVersion, skipCompatibilityCheck, exclusions);
    Objects.requireNonNull(licenses, "Artifacts must have a license");
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
    if (!super.equals(o)) {
      return false;
    }

    final ReifiedArtifact that = (ReifiedArtifact) o;
    return licenses.equals(that.licenses);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + licenses.hashCode();
    return result;
  }
}
