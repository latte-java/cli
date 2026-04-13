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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.lattejava.domain.Version;

/**
 * <p>
 * This class defines an artifact as it exists across all projects, dependencies, etc. This class is the representation
 * of the artifact that is defined by its group, project, name, type, and version. This object is also the representation
 * of a dependency between two project's.
 * </p>
 * <p>
 * See the {@link #Artifact(String)} constructor for String formats of artifacts.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class Artifact {
  public final List<ArtifactID> exclusions;

  public final ArtifactID id;

  public final String nonSemanticVersion;

  public final boolean skipCompatibilityCheck;

  public final Version version;

  /**
   * Shorthand for {@link Artifact#Artifact(ArtifactID, Version, List)} that passes in null for the exclusions.
   */
  public Artifact(ArtifactID id, Version version) {
    this(id, version, null);
  }

  /**
   * Constructs an Artifact with the given ID and version.
   *
   * @param id      The artifact ID (group, project, name, type).
   * @param version The version of the artifact.
   */
  public Artifact(ArtifactID id, Version version, List<ArtifactID> exclusions) {
    this(id, version, null, exclusions);
  }

  /**
   * Shorthand for {@link Artifact#Artifact(ArtifactID, Version, String, boolean, List)} that passes in false for the
   * compatibility check.
   */
  public Artifact(ArtifactID id, Version version, String nonSemanticVersion, List<ArtifactID> exclusions) {
    this(id, version, nonSemanticVersion, false, exclusions);
  }

  /**
   * Constructs an Artifact with the given ID and version.
   *
   * @param id                     The artifact ID (group, project, name, type).
   * @param version                The version of the artifact.
   * @param nonSemanticVersion     The non-semantic version.
   * @param skipCompatibilityCheck Whether compatibility checks should be skipped or run.
   * @param exclusions             Any exclusions.
   */
  public Artifact(ArtifactID id, Version version, String nonSemanticVersion, boolean skipCompatibilityCheck, List<ArtifactID> exclusions) {
    Objects.requireNonNull(id, "Artifacts must have an ArtifactID");
    Objects.requireNonNull(version, "Artifacts must have a Version");

    this.id = id;
    this.nonSemanticVersion = nonSemanticVersion;
    this.skipCompatibilityCheck = skipCompatibilityCheck;
    this.version = version;

    if (exclusions != null) {
      this.exclusions = List.copyOf(exclusions);
    } else {
      this.exclusions = Collections.emptyList();
    }
  }

  /**
   * Shorthand for {@link Artifact#Artifact(String, String, boolean, List)} that passes in null for the exclusions.
   */
  public Artifact(String spec) {
    this(spec, null, false, null);
  }

  /**
   * <p>
   * Parses the given specification to build an artifact. The currently supported spec formats are:
   * </p>
   * <pre>
   *   group:project:version
   *   group:project:version:type
   *   group:project:name:version:type
   * </pre>
   * <p>
   * Examples:
   * </p>
   * <pre>
   *   org.lattejava:cli:0.1
   *   org.lattejava:cli:0.1:jar
   *   org.lattejava:cli:some-other-artifact:0.1:jar
   * </pre>
   *
   * @param spec                   The spec.
   * @param nonSemanticVersion     The non-semantic version of this artifact that the project might depend on.
   * @param skipCompatibilityCheck Determines if the compatibility check is skipped for this artifact or not.
   * @param exclusions             (Optional) Any exclusions of the artifact.
   */
  public Artifact(String spec, String nonSemanticVersion, boolean skipCompatibilityCheck, List<ArtifactID> exclusions) {
    Objects.requireNonNull(spec, "Artifacts must have a full specification");

    var artifactSpec = new ArtifactSpec(spec);
    this.nonSemanticVersion = nonSemanticVersion;
    this.skipCompatibilityCheck = skipCompatibilityCheck;
    this.id = artifactSpec.id;
    this.version = new Version(artifactSpec.version);

    if (exclusions != null) {
      this.exclusions = List.copyOf(exclusions);
    } else {
      this.exclusions = Collections.emptyList();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof final Artifact artifact)) {
      return false;
    }

    return Objects.equals(exclusions, artifact.exclusions) && Objects.equals(id, artifact.id) && Objects.equals(version, artifact.version);
  }

  /**
   * <p>
   * Returns the artifact source file name in the alternative (Maven style) format. This does not include any path
   * information at all and would look something like this:
   * </p>
   * <pre>
   * common-collections-2.1-sources.jar
   * </pre>
   *
   * @return The source file name.
   */
  public String getArtifactAlternativeSourceFile() {
    return prefix() + "-sources." + id.type;
  }

  /**
   * <p>
   * Returns the artifact file name. This does not include any path information at all and would look something like
   * this:
   * </p>
   * <pre>
   * common-collections-2.1.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactFile() {
    return prefix() + "." + id.type;
  }

  /**
   * <p>
   * Returns the artifact MetaData file name. This does not include any path information at all and would look something
   * like this:
   * </p>
   * <pre>
   * common-collections-2.1.jar.amd
   * </pre>
   *
   * @return The MetaData file name.
   */
  public String getArtifactMetaDataFile() {
    return prefix() + "." + id.type + ".amd";
  }

  /**
   * <p>
   * Returns the artifact source file name in the alternative (Maven style) format with the non-semantic version. This
   * does not include any path information at all and would look something like this:
   * </p>
   * <pre>
   * common-collections-2.1.1.Final-sources.jar
   * </pre>
   *
   * @return The source file name.
   */
  public String getArtifactNonSemanticAlternativeSourceFile() {
    return nonSemanticPrefix() + "-sources." + id.type;
  }

  /**
   * <p>
   * Returns the artifact file name using the non-semantic version. This does not include any path information at all
   * and would look something like this:
   * </p>
   * <pre>
   * common-collections-2.1.1.Final.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactNonSemanticFile() {
    return nonSemanticPrefix() + "." + id.type;
  }

  /**
   * <p>
   * Returns the artifact POM file name that uses the non-semantic version (if there is one). This does not include any
   * path information at all and would look something like this:
   * </p>
   * <pre>
   * common-collections-2.1.1.Final.pom
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactNonSemanticPOMFile() {
    return nonSemanticPrefix() + ".pom";
  }

  /**
   * <p>
   * Returns the artifact POM file name. This does not include any path information at all and would look something like
   * this:
   * </p>
   * <pre>
   * common-collections-2.1.pom
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactPOMFile() {
    return id.project + "-" + version + ".pom";
  }

  /**
   * <p>
   * Returns the artifact source file name. This does not include any path information at all and would look something
   * like this:
   * </p>
   * <pre>
   * common-collections-2.1-src.jar
   * </pre>
   *
   * @return The source file name.
   */
  public String getArtifactSourceFile() {
    return prefix() + "-src." + id.type;
  }

  /**
   * <p>
   * Returns the artifact test file name. This does not include any path information at all and would look something
   * like this:
   * </p>
   * <pre>
   * common-collections-test-2.1.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactTestFile() {
    return id.name + "-test-" + version + "." + id.type;
  }

  /**
   * <p>
   * Returns the artifact test source file name. This does not include any path information at all and would look
   * something like this:
   * </p>
   * <pre>
   * common-collections-test-2.1.jar
   * </pre>
   *
   * @return The file name.
   */
  public String getArtifactTestSourceFile() {
    return id.name + "-test-" + version + "-src." + id.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(exclusions, id, version);
  }

  /**
   * @return Whether the version of this artifact is an integration build version.
   */
  @SuppressWarnings("unused")
  public boolean isIntegrationBuild() {
    return version.isIntegration();
  }

  @Override
  public String toString() {
    return id.group + ":" + id.project + ":" + id.name + ":" + version + ":" + id.type;
  }

  /**
   * Returns the shortest unambiguous spec string for this artifact. Uses the 3-part form
   * {@code group:project:version} when the name equals the project and the type is {@code jar}, otherwise uses the
   * full 5-part form.
   *
   * @return The shortest spec string.
   */
  public String toShortestString() {
    String versionStr = nonSemanticVersion != null ? nonSemanticVersion : version.toString();
    if (id.name.equals(id.project) && id.type.equals("jar")) {
      return id.group + ":" + id.project + ":" + versionStr;
    }
    return id.group + ":" + id.project + ":" + id.name + ":" + versionStr + ":" + id.type;
  }

  private String nonSemanticPrefix() {
    return id.project + "-" + nonSemanticVersion;
  }

  private String prefix() {
    return id.name + "-" + version;
  }
}
