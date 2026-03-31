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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

/**
 * Tests the FileSet class.
 *
 * @author Brian Pontarelli
 */
public class FileSetTest extends BaseUnitTest {
  @Test
  public void toFileInfos() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve("src/main/java"));
    List<FileInfo> infos = fileSet.toFileInfos();
    List<Path> actual = infos.stream().map((info) -> info.origin).collect(Collectors.toList());
    assertEquals(actual, Arrays.asList(
        projectDir.resolve("src/main/java/org/savantbuild/io/ArchiveFileSet.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Copier.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Directory.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileInfo.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileSet.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Filter.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Tools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipTools.java")
    ), "Actual is " + actual);
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("org/savantbuild/io/ArchiveFileSet.java"),
        Paths.get("org/savantbuild/io/Copier.java"),
        Paths.get("org/savantbuild/io/Directory.java"),
        Paths.get("org/savantbuild/io/FileInfo.java"),
        Paths.get("org/savantbuild/io/FileSet.java"),
        Paths.get("org/savantbuild/io/FileTools.java"),
        Paths.get("org/savantbuild/io/Filter.java"),
        Paths.get("org/savantbuild/io/Tools.java"),
        Paths.get("org/savantbuild/io/jar/JarBuilder.java"),
        Paths.get("org/savantbuild/io/jar/JarTools.java"),
        Paths.get("org/savantbuild/io/tar/TarBuilder.java"),
        Paths.get("org/savantbuild/io/tar/TarTools.java"),
        Paths.get("org/savantbuild/io/zip/ZipBuilder.java"),
        Paths.get("org/savantbuild/io/zip/ZipTools.java")
    ));
  }

  @Test
  public void toFileInfosWithExcludePatterns() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve("src/main/java"), null, asList(Pattern.compile(".*/jar/.*")));
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.stream().map((info) -> info.origin).collect(Collectors.toList()), Arrays.asList(
        projectDir.resolve("src/main/java/org/savantbuild/io/ArchiveFileSet.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Copier.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Directory.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileInfo.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileSet.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Filter.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Tools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipTools.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("org/savantbuild/io/ArchiveFileSet.java"),
        Paths.get("org/savantbuild/io/Copier.java"),
        Paths.get("org/savantbuild/io/Directory.java"),
        Paths.get("org/savantbuild/io/FileInfo.java"),
        Paths.get("org/savantbuild/io/FileSet.java"),
        Paths.get("org/savantbuild/io/FileTools.java"),
        Paths.get("org/savantbuild/io/Filter.java"),
        Paths.get("org/savantbuild/io/Tools.java"),
        Paths.get("org/savantbuild/io/tar/TarBuilder.java"),
        Paths.get("org/savantbuild/io/tar/TarTools.java"),
        Paths.get("org/savantbuild/io/zip/ZipBuilder.java"),
        Paths.get("org/savantbuild/io/zip/ZipTools.java")
    ));
  }

  @Test
  public void toFileInfosWithIncludeAndExcludePatterns() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve("src/main/java"), asList(Pattern.compile(".*/io/.*")), asList(Pattern.compile(".*FileSet\\.java")));
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.stream().map((info) -> info.origin).collect(Collectors.toList()), Arrays.asList(
        projectDir.resolve("src/main/java/org/savantbuild/io/Copier.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Directory.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileInfo.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/FileTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Filter.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/Tools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipTools.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("org/savantbuild/io/Copier.java"),
        Paths.get("org/savantbuild/io/Directory.java"),
        Paths.get("org/savantbuild/io/FileInfo.java"),
        Paths.get("org/savantbuild/io/FileTools.java"),
        Paths.get("org/savantbuild/io/Filter.java"),
        Paths.get("org/savantbuild/io/Tools.java"),
        Paths.get("org/savantbuild/io/jar/JarBuilder.java"),
        Paths.get("org/savantbuild/io/jar/JarTools.java"),
        Paths.get("org/savantbuild/io/tar/TarBuilder.java"),
        Paths.get("org/savantbuild/io/tar/TarTools.java"),
        Paths.get("org/savantbuild/io/zip/ZipBuilder.java"),
        Paths.get("org/savantbuild/io/zip/ZipTools.java")
    ));
  }

  @Test
  public void toFileInfosWithIncludePatterns() throws Exception {
    FileSet fileSet = new FileSet(projectDir.resolve("src/main/java"), asList(Pattern.compile(".*/jar/.*")), null);
    List<FileInfo> infos = fileSet.toFileInfos();
    assertEquals(infos.stream().map((info) -> info.origin).collect(Collectors.toList()), Arrays.asList(
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarTools.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("org/savantbuild/io/jar/JarBuilder.java"),
        Paths.get("org/savantbuild/io/jar/JarTools.java")
    ));
  }
}
