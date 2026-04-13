/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.cli.parser.groovy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;

/**
 * Utilities for reading, writing, and transforming {@code project.latte} files. Uses {@link GroovySourceTools} for
 * lexer-based block location and provides higher-level operations like replacing the dependencies block.
 *
 * @author Brian Pontarelli
 */
public class ProjectFileTools {
  /**
   * Reads the project file, replaces (or inserts) the {@code dependencies} block, and writes it back.
   *
   * @param projectFile  The path to the project.latte file.
   * @param dependencies The dependencies to render into the file.
   */
  public static void writeDependencies(Path projectFile, Dependencies dependencies) {
    String content = readProjectFile(projectFile);
    content = replaceDependenciesBlock(content, dependencies);
    writeProjectFile(projectFile, content);
  }

  /**
   * Replaces the {@code dependencies} block in a Groovy project source string with a freshly generated one. If no
   * {@code dependencies} block exists, one is inserted inside the {@code project} block.
   *
   * @param source       The Groovy source text.
   * @param dependencies The dependencies to render.
   * @return The modified source text.
   * @throws IllegalArgumentException If no {@code project} block can be found when inserting a new dependencies block.
   */
  public static String replaceDependenciesBlock(String source, Dependencies dependencies) {
    GroovySourceTools.Block depsBlock = GroovySourceTools.findBlock(source, "dependencies", 1);

    if (depsBlock != null) {
      int lineStart = source.lastIndexOf('\n', depsBlock.start()) + 1;
      String indent = source.substring(lineStart, depsBlock.start());
      String newBlock = generateDependenciesBlock(dependencies, indent);
      return source.substring(0, depsBlock.start()) + newBlock + source.substring(depsBlock.end());
    }

    // No dependencies block — insert one inside the project block
    GroovySourceTools.Block projectBlock = GroovySourceTools.findBlock(source, "project", 0);
    if (projectBlock == null) {
      throw new IllegalArgumentException("Could not find a project block in the source.");
    }

    int lineStart = source.lastIndexOf('\n', projectBlock.start()) + 1;
    String outerIndent = source.substring(lineStart, projectBlock.start());
    String indent = outerIndent + "  ";
    int insertPos = projectBlock.end() - 1;
    String newBlock = "\n" + indent + generateDependenciesBlock(dependencies, indent) + "\n";
    return source.substring(0, insertPos) + newBlock + source.substring(insertPos);
  }

  /**
   * Generates a Groovy {@code dependencies} block string from a {@link Dependencies} object.
   *
   * @param dependencies The dependencies to render.
   * @param indent       The indentation prefix for the block.
   * @return The generated block text.
   */
  public static String generateDependenciesBlock(Dependencies dependencies, String indent) {
    StringBuilder sb = new StringBuilder();
    sb.append("dependencies {\n");

    for (DependencyGroup group : dependencies.groups.values()) {
      sb.append(indent).append("  group(name: \"").append(group.name).append("\"");
      if (!group.export) {
        sb.append(", export: false");
      }
      sb.append(") {\n");

      for (Artifact dep : group.dependencies) {
        sb.append(indent).append("    dependency(id: \"").append(dep.toShortestString()).append("\"");
        if (dep.skipCompatibilityCheck) {
          sb.append(", skipCompatibilityCheck: true");
        }
        if (dep.exclusions.isEmpty()) {
          sb.append(")\n");
        } else {
          sb.append(") {\n");
          for (ArtifactID exclusion : dep.exclusions) {
            sb.append(indent).append("      exclusion(id: \"").append(exclusion.toShortestString()).append("\")\n");
          }
          sb.append(indent).append("    }\n");
        }
      }

      sb.append(indent).append("  }\n");
    }

    sb.append(indent).append("}");
    return sb.toString();
  }

  /**
   * Reads the project file content as a string.
   *
   * @param projectFile The path to the project.latte file.
   * @return The file content.
   */
  public static String readProjectFile(Path projectFile) {
    try {
      return Files.readString(projectFile, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to read project.latte: " + e.getMessage());
    }
  }

  /**
   * Writes content to the project file.
   *
   * @param projectFile The path to the project.latte file.
   * @param content     The content to write.
   */
  public static void writeProjectFile(Path projectFile, String content) {
    try {
      Files.writeString(projectFile, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to write project.latte: " + e.getMessage());
    }
  }
}
