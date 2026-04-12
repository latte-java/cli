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
import org.lattejava.dep.domain.Dependencies;

/**
 * Utilities for reading and writing {@code project.latte} files. This bridges the pure source-transformation methods in
 * {@link GroovySourceTools} with file I/O.
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
    content = GroovySourceTools.replaceDependenciesBlock(content, dependencies);
    writeProjectFile(projectFile, content);
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
