/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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
