/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the ArchiveFileSet class.
 *
 * @author Brian Pontarelli
 */
public class ArchiveFileSetTest extends BaseUnitTest {
  @Test
  public void toFileInfosNoPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve("src/main/java"), null);
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 122);
    // Spot-check a few known files
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.contains(projectDir.resolve("src/main/java/org/lattejava/io/FileSet.java")));
    assertTrue(origins.contains(projectDir.resolve("src/main/java/org/lattejava/io/Copier.java")));
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("org/lattejava/io/FileSet.java")));
    assertTrue(relatives.contains(Paths.get("org/lattejava/io/Copier.java")));
  }

  @Test
  public void toFileInfosWithPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve("src/main/java"), "some-directory-1.0");
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 122);
    // Spot-check origins are unchanged (prefix doesn't affect origin)
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.contains(projectDir.resolve("src/main/java/org/lattejava/io/FileSet.java")));
    // Spot-check relative paths include the prefix
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("some-directory-1.0/org/lattejava/io/FileSet.java")));
    assertTrue(relatives.contains(Paths.get("some-directory-1.0/org/lattejava/io/Copier.java")));
  }

  @Test
  public void toFileInfosWithDeepPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve("src/main/java"), "usr/local/inversoft/main");
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 122);
    // Spot-check origins
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.contains(projectDir.resolve("src/main/java/org/lattejava/io/FileSet.java")));
    // Spot-check relative paths include the deep prefix
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("usr/local/inversoft/main/org/lattejava/io/FileSet.java")));

    Set<Directory> directories = fileSet.toDirectories();
    assertEquals(directories, new HashSet<>(asList(
        new Directory("usr"),
        new Directory("usr/local"),
        new Directory("usr/local/inversoft"),
        new Directory("usr/local/inversoft/main"),
        new Directory("usr/local/inversoft/main/org"),
        new Directory("usr/local/inversoft/main/org/lattejava"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/command"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/domain"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/parser"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/parser/groovy"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/plugin"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/plugin/groovy"),
        new Directory("usr/local/inversoft/main/org/lattejava/cli/runtime"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep/domain"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep/domain/json"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep/graph"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep/maven"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep/workflow"),
        new Directory("usr/local/inversoft/main/org/lattejava/dep/workflow/process"),
        new Directory("usr/local/inversoft/main/org/lattejava/domain"),
        new Directory("usr/local/inversoft/main/org/lattejava/io"),
        new Directory("usr/local/inversoft/main/org/lattejava/io/jar"),
        new Directory("usr/local/inversoft/main/org/lattejava/io/tar"),
        new Directory("usr/local/inversoft/main/org/lattejava/io/zip"),
        new Directory("usr/local/inversoft/main/org/lattejava/lang"),
        new Directory("usr/local/inversoft/main/org/lattejava/net"),
        new Directory("usr/local/inversoft/main/org/lattejava/output"),
        new Directory("usr/local/inversoft/main/org/lattejava/security"),
        new Directory("usr/local/inversoft/main/org/lattejava/util")
    )));
  }
}
