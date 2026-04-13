/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.dep.domain;

import java.util.Objects;

import static java.util.Arrays.stream;

/**
 * This class contains the properties that define an artifacts identity. Any two artifacts whose identity match are
 * considered the same artifact. All other properties associated with the artifact usually determine the artifacts
 * variant (such as version).
 *
 * @author Brian Pontarelli
 */
public class ArtifactID implements Comparable<ArtifactID> {
  public final String group;

  public final String name;

  public final String project;

  public final String type;

  /**
   * Constructs an artifact id, which is composed of a group, project, name, and type
   *
   * @param group   the artifact group
   * @param project the artifact project
   * @param name    the artifact name
   * @param type    the artifact type
   * @throws NullPointerException If any of the arguments are null.
   */
  public ArtifactID(String group, String project, String name, String type) throws NullPointerException {
    Objects.requireNonNull(group, "Artifacts must have a group");
    Objects.requireNonNull(project, "Artifacts must have a project");
    Objects.requireNonNull(name, "Artifacts must have a name");
    Objects.requireNonNull(type, "Artifacts must have a type");
    this.group = group;
    this.project = project;
    this.name = name;
    this.type = type;
  }

  /**
   * Constructs an artifact id using the shorthand.
   *
   * @param spec The spec.
   * @throws NullPointerException If any of the arguments are null.
   */
  public ArtifactID(String spec) throws NullPointerException {
    Objects.requireNonNull(spec, "Artifacts must have a full specification");

    String[] parts = spec.split(":");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid artifact ID specification [" + spec + "]. It must have 2, 3, or 4 parts");
    }

    if (stream(parts).anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException("Invalid artifact ID specification [" + spec + "]. One of the parts is empty (i.e. foo::bar");
    }

    if (parts.length == 2) {
      group = parts[0];
      project = parts[1];
      name = parts[1];
      type = "jar";
    } else if (parts.length == 3) {
      group = parts[0];
      project = parts[1];
      name = parts[1];
      type = parts[2];
    } else if (parts.length == 4) {
      group = parts[0];
      project = parts[1];
      name = parts[2];
      type = parts[3];
    } else {
      throw new IllegalArgumentException("Invalid artifact ID specification [" + spec + "]. It must have 2, 3, or 4 parts");
    }
  }

  @Override
  public int compareTo(ArtifactID other) {
    Objects.requireNonNull(other);
    int diff = group.compareTo(other.group);
    if (diff == 0) {
      diff = name.compareTo(other.name);
    }
    if (diff == 0) {
      diff = project.compareTo(other.project);
    }
    if (diff == 0) {
      diff = type.compareTo(other.type);
    }
    return diff;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ArtifactID that = (ArtifactID) o;
    return group.equals(that.group) && name.equals(that.name) && project.equals(that.project) && type.equals(that.type);
  }

  public int hashCode() {
    int result;
    result = group.hashCode();
    result = 31 * result + project.hashCode();
    result = 31 * result + name.hashCode();
    result = 31 * result + type.hashCode();
    return result;
  }

  /**
   * Returns the shortest unambiguous spec string for this artifact ID. Uses the 2-part form {@code group:project}
   * when the name equals the project and the type is {@code jar}. Uses the 3-part form {@code group:project:type}
   * when the name equals the project but the type is not {@code jar}. Otherwise uses the full 4-part form.
   *
   * @return The shortest spec string.
   */
  public String toShortestString() {
    if (name.equals(project) && type.equals("jar")) {
      return group + ":" + project;
    }
    if (name.equals(project)) {
      return group + ":" + project + ":" + type;
    }
    return toString();
  }

  public String toString() {
    return group + ":" + project + ":" + name + ":" + type;
  }
}
