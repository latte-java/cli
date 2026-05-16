/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.java

import java.nio.file.Files
import java.nio.file.Path

import org.lattejava.cli.parser.groovy.GroovyTools

/**
 * Delegate for the JarJar method's closure that allows rules to be defined inline.
 *
 * @author Brian Pontarelli
 */
class JarJarDelegate {
  private final Map<String, String> rules = [:]

  /**
   * Defines a single JarJar rewrite rule.
   */
  void rule(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["from", "to"], ["from", "to"], ["from": String.class, "to": String.class])) {
      fail("You must supply rules as nested elements of the JarJar method like this::\n\n" +
          "  java.jarjar(dependencyGroup: \"nasty-deps\", outputDirectory: \"build/classes/main\") {\n" +
          "    rule(from: \"foo\", to: \"bar\"\n" +
          "   }\n")
    }

    rules.put(attributes["from"].toString(), attributes["to"].toString())
  }

  /**
   * Creates a temp file and converts the rules to the JarJar file format.
   *
   * @return The temp rules file.
   */
  Path buildRulesFile() {
    Path tempFile = Files.createTempFile("jarjar", "rules")
    rules.forEach { from, to ->
      tempFile.append("rule ${from} ${to}\n")
    }

    return tempFile
  }
}