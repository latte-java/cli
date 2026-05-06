/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the FileTools.
 *
 * @author Brian Pontarelli
 */
public class FileToolsTest extends BaseUnitTest {
  @Test
  public void modifiedFiles() throws Exception {
    List<Path> modifiedFiles = FileTools.modifiedFiles(projectDir.resolve("src/main/java"), projectDir.resolve("build/classes/main"),
        FileTools.extensionFilter(".java"),
        FileTools.extensionMapper(".java", ".class"));
    assertEquals(modifiedFiles.size(), 0);

    FileTools.touch(projectDir.resolve("src/main/java/org/lattejava/io/FileTools.java"),
        projectDir.resolve("src/main/java/org/lattejava/io/FileSet.java"));
    modifiedFiles = FileTools.modifiedFiles(projectDir.resolve("src/main/java"), projectDir.resolve("build/classes/main"),
        FileTools.extensionFilter(".java"),
        FileTools.extensionMapper(".java", ".class"));
    assertEquals(modifiedFiles, asList(Paths.get("org/lattejava/io/FileSet.java"), Paths.get("org/lattejava/io/FileTools.java")));
  }

  @Test
  public void prune() throws Exception {
    Path path = projectDir.resolve("build/test-prune/sub-dir");
    Files.createDirectories(path);

    Path file = path.resolve("test.txt");
    Files.write(file, "Testing 123".getBytes());
    assertTrue(Files.isRegularFile(file));

    FileTools.prune(path);
    assertFalse(Files.isDirectory(path));
  }
}
