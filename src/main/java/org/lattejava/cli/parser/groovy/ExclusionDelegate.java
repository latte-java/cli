/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser.groovy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.cli.parser.ParseException;

/**
 * Groovy delegate that defines the exclusions for a single dependency.
 *
 * @author Brian Pontarelli
 */
public class ExclusionDelegate {
  private final List<ArtifactID> exclusions = new ArrayList<>();

  /**
   * Defines am exclusion. This takes a Map of attributes but only the {@code id} attributes is required. This attribute
   * defines the exclusion using the shorthand notation.
   *
   * @param attributes The attributes.
   * @return Nothing
   * @see Artifact#Artifact(String)
   */
  public ArtifactID exclusion(Map<String, Object> attributes) {
    if (!GroovyTools.hasAttributes(attributes, "id")) {
      throw new ParseException("Invalid exclusion definition. It must have the id attribute like this:\n\n" +
          "  exclusion(id: \"org.example:foo\")");
    }

    String id = GroovyTools.toString(attributes, "id");
    ArtifactID exclusion = new ArtifactID(id);
    exclusions.add(exclusion);
    return exclusion;
  }

  public List<ArtifactID> getExclusions() {
    return exclusions;
  }
}
