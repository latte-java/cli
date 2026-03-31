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

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

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
    assertEquals(infos.stream().map((info) -> info.origin).collect(Collectors.toList()), Arrays.asList(
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
        Paths.get("org/savantbuild/io/jar/JarBuilder.java"),
        Paths.get("org/savantbuild/io/jar/JarTools.java"),
        Paths.get("org/savantbuild/io/tar/TarBuilder.java"),
        Paths.get("org/savantbuild/io/tar/TarTools.java"),
        Paths.get("org/savantbuild/io/zip/ZipBuilder.java"),
        Paths.get("org/savantbuild/io/zip/ZipTools.java")
    ));
  }

  @Test
  public void toFileInfosWithPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve("src/main/java"), "some-directory-1.0");
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
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipTools.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("some-directory-1.0/org/savantbuild/io/ArchiveFileSet.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/Copier.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/Directory.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/FileInfo.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/FileSet.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/FileTools.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/Filter.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/Tools.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/jar/JarBuilder.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/jar/JarTools.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/tar/TarBuilder.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/tar/TarTools.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/zip/ZipBuilder.java"),
        Paths.get("some-directory-1.0/org/savantbuild/io/zip/ZipTools.java")
    ));
  }

  @Test
  public void toFileInfosWithDeepPrefix() throws Exception {
    ArchiveFileSet fileSet = new ArchiveFileSet(projectDir.resolve("src/main/java"), "usr/local/inversoft/main");
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
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/jar/JarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/tar/TarTools.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipBuilder.java"),
        projectDir.resolve("src/main/java/org/savantbuild/io/zip/ZipTools.java")
    ));
    assertEquals(infos.stream().map((info) -> info.relative).collect(Collectors.toList()), asList(
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/ArchiveFileSet.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/Copier.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/Directory.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/FileInfo.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/FileSet.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/FileTools.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/Filter.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/Tools.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/jar/JarBuilder.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/jar/JarTools.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/tar/TarBuilder.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/tar/TarTools.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/zip/ZipBuilder.java"),
        Paths.get("usr/local/inversoft/main/org/savantbuild/io/zip/ZipTools.java")
    ));

    Set<Directory> directories = fileSet.toDirectories();
    assertEquals(directories, new HashSet<>(asList(
        new Directory("usr"),
        new Directory("usr/local"),
        new Directory("usr/local/inversoft"),
        new Directory("usr/local/inversoft/main"),
        new Directory("usr/local/inversoft/main/org"),
        new Directory("usr/local/inversoft/main/org/savantbuild"),
        new Directory("usr/local/inversoft/main/org/savantbuild/io"),
        new Directory("usr/local/inversoft/main/org/savantbuild/io/jar"),
        new Directory("usr/local/inversoft/main/org/savantbuild/io/tar"),
        new Directory("usr/local/inversoft/main/org/savantbuild/io/zip")
    )));
  }
}
