/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the FileSet class.
 * <p>
 * These tests run against the frozen fixture tree under {@code src/test/resources/archive-fixture/main} rather than the
 * live project source, so the exact counts and file lists never drift as the project changes.
 *
 * @author Brian Pontarelli
 */
public class FileSetTest extends BaseUnitTest {
  private static final Path mainFixture = Paths.get("src/test/resources/archive-fixture/main");

  @Test
  public void toFileInfos() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve(mainFixture));
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 6);
    // Spot-check a few known files are present
    List<Path> actual = infos.stream().map((info) -> info.origin).toList();
    assertTrue(actual.contains(projectDir.resolve(mainFixture).resolve("com/example/app/App.java")));
    assertTrue(actual.contains(projectDir.resolve(mainFixture).resolve("com/example/app/io/Reader.java")));
    List<Path> relatives = infos.stream().map((info) -> info.relative).toList();
    assertTrue(relatives.contains(Paths.get("com/example/app/App.java")));
    assertTrue(relatives.contains(Paths.get("com/example/app/io/Reader.java")));
  }

  @Test
  public void toFileInfosWithExcludePatterns() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve(mainFixture), null, List.of(Pattern.compile(".*/jar/.*")));
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.size(), 4);
    // Verify jar files are excluded
    List<Path> origins = infos.stream().map((info) -> info.origin).toList();
    assertTrue(origins.stream().noneMatch(p -> p.toString().contains("/jar/")));
    // Spot-check non-jar files are still present
    assertTrue(origins.contains(projectDir.resolve(mainFixture).resolve("com/example/app/Util.java")));
    assertTrue(origins.contains(projectDir.resolve(mainFixture).resolve("com/example/app/io/Writer.java")));
  }

  @Test
  public void toFileInfosWithIncludeAndExcludePatterns() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve(mainFixture), List.of(Pattern.compile(".*/io/.*")),
        List.of(Pattern.compile(".*Reader\\.java")));
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.stream().map((info) -> info.origin).collect(Collectors.toList()), Arrays.asList(
        projectDir.resolve(mainFixture).resolve("com/example/app/io/Writer.java"),
        projectDir.resolve(mainFixture).resolve("com/example/app/io/jar/JarHelper.java"),
        projectDir.resolve(mainFixture).resolve("com/example/app/io/jar/JarTool.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("com/example/app/io/Writer.java"),
        Paths.get("com/example/app/io/jar/JarHelper.java"),
        Paths.get("com/example/app/io/jar/JarTool.java")
    ));
  }

  @Test
  public void toFileInfosWithIncludePatterns() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve(mainFixture), List.of(Pattern.compile(".*/jar/.*")), null);
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.stream().map((info) -> info.origin).collect(Collectors.toList()), Arrays.asList(
        projectDir.resolve(mainFixture).resolve("com/example/app/io/jar/JarHelper.java"),
        projectDir.resolve(mainFixture).resolve("com/example/app/io/jar/JarTool.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("com/example/app/io/jar/JarHelper.java"),
        Paths.get("com/example/app/io/jar/JarTool.java")
    ));
  }
}
