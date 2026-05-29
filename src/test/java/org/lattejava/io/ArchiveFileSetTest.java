/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
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
 * <p>
 * These tests run against the frozen fixture tree under {@code src/test/resources/archive-fixture/main} rather than the
 * live project source, so the exact counts and directory sets never drift as the project changes.
 *
 * @author Brian Pontarelli
 */
public class ArchiveFileSetTest extends BaseUnitTest {
  private static final Path mainFixture = Paths.get("src/test/resources/archive-fixture/main");

  @Test
  public void toFileInfosNoPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve(mainFixture), null);
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 6);
    // Spot-check a few known files
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.contains(projectDir.resolve(mainFixture).resolve("com/example/app/App.java")));
    assertTrue(origins.contains(projectDir.resolve(mainFixture).resolve("com/example/app/io/Reader.java")));
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("com/example/app/App.java")));
    assertTrue(relatives.contains(Paths.get("com/example/app/io/Reader.java")));
  }

  @Test
  public void toFileInfosWithPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve(mainFixture), "some-directory-1.0");
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 6);
    // Spot-check origins are unchanged (prefix doesn't affect origin)
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.contains(projectDir.resolve(mainFixture).resolve("com/example/app/App.java")));
    // Spot-check relative paths include the prefix
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("some-directory-1.0/com/example/app/App.java")));
    assertTrue(relatives.contains(Paths.get("some-directory-1.0/com/example/app/io/Reader.java")));
  }

  @Test
  public void toFileInfosWithDeepPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve(mainFixture), "usr/local/inversoft/main");
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 6);
    // Spot-check origins
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.contains(projectDir.resolve(mainFixture).resolve("com/example/app/App.java")));
    // Spot-check relative paths include the deep prefix
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("usr/local/inversoft/main/com/example/app/App.java")));

    Set<Directory> directories = fileSet.toDirectories();
    assertEquals(directories, new HashSet<>(asList(
        new Directory("usr"),
        new Directory("usr/local"),
        new Directory("usr/local/inversoft"),
        new Directory("usr/local/inversoft/main"),
        new Directory("usr/local/inversoft/main/com"),
        new Directory("usr/local/inversoft/main/com/example"),
        new Directory("usr/local/inversoft/main/com/example/app"),
        new Directory("usr/local/inversoft/main/com/example/app/io"),
        new Directory("usr/local/inversoft/main/com/example/app/io/jar")
    )));
  }
}
