/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser.groovy;

import java.util.Map;

import org.lattejava.domain.Version;
import org.lattejava.cli.parser.ParseException;

/**
 * Groovy delegate that captures the semantic version mappings for a project.
 *
 * @author Brian Pontarelli
 */
public class SemanticVersionDelegate {
  public final Map<String, Version> mapping;

  public final Map<String, String> rangeMapping;

  public SemanticVersionDelegate(Map<String, Version> mapping, Map<String, String> rangeMapping) {
    this.mapping = mapping;
    this.rangeMapping = rangeMapping;
  }

  public void mapping(Map<String, Object> attributes) {
    if (!GroovyTools.hasAttributes(attributes, "id", "version")) {
      throw new ParseException("""
          Invalid mapping definition. It must have an [id] and a [version] attribute like this:
          
            mapping(id: "org.badver:badver:1.0.0.Final", version: "1.0.0")
          """);
    }

    String id = GroovyTools.toString(attributes, "id");
    String version = GroovyTools.toString(attributes, "version");
    mapping.put(id, new Version(version));
  }

  public void rangeMapping(Map<String, Object> attributes) {
    if (!GroovyTools.hasAttributes(attributes, "id", "version")) {
      throw new ParseException("""
          Invalid rangeMapping definition. It must have an [id] and a [version] attribute like this:
          
            rangeMapping(id: "org.range:mc-range-face:[1.0,2.0)", version: "1.0")
          
          Note, the version should be a concrete version found in the Maven repository. If this version is not
          semantic, it is possible you will also require a version mapping.
          """);
    }

    String id = GroovyTools.toString(attributes, "id");
    String version = GroovyTools.toString(attributes, "version");
    rangeMapping.put(id, version);
  }
}
