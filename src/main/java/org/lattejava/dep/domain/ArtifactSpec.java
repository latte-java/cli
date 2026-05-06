/*
 * Copyright (c) 2024-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import static java.util.Arrays.stream;

/**
 * An artifact specification that complies with Latte and Maven definitions.
 *
 * @author Brady Wied
 * @author Brian Pontarelli
 */
public class ArtifactSpec {
  public final ArtifactID id;

  public final String mavenSpec;

  public final String version;

  public ArtifactSpec(String spec) {
    String[] parts = spec.split(":");
    if (parts.length < 3) {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. It must have 3, 4, or 5 parts");
    }

    if (stream(parts).anyMatch(String::isEmpty)) {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. One of the parts is empty (i.e. foo::3.0");
    }

    if (parts.length == 3) {
      id = new ArtifactID(parts[0], parts[1], parts[1], "jar");
      version = parts[2];
    } else if (parts.length == 4) {
      id = new ArtifactID(parts[0], parts[1], parts[1], parts[3]);
      version = parts[2];
    } else if (parts.length == 5) {
      id = new ArtifactID(parts[0], parts[1], parts[2], parts[4]);
      version = parts[3];
    } else {
      throw new IllegalArgumentException("Invalid artifact specification [" + spec + "]. It must have 3, 4, or 5 parts");
    }

    mavenSpec = id.group + ":" + id.name + ":" + version;
  }
}
