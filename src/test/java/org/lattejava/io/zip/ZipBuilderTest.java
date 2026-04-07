/*
 * Copyright (c) 2014-2018, Inversoft Inc., All Rights Reserved
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
package org.lattejava.io.zip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.lattejava.BaseUnitTest;
import org.lattejava.io.ArchiveFileSet;
import org.lattejava.io.Directory;
import org.lattejava.io.FileSet;
import org.lattejava.io.FileTools;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static org.testng.Assert.*;

/**
 * Tests the ZipBuilder.
 *
 * @author Brian Pontarelli
 */
public class ZipBuilderTest extends BaseUnitTest {
  private static void assertZipContains(Path file, String... entries) throws Exception {
    try (FileSystem zipFs = FileSystems.newFileSystem(file, Map.of("enablePosixFileAttributes", true))) {
      stream(entries).forEach((entry) -> {
        Path entryPath = zipFs.getPath(entry);
        assertTrue(Files.exists(entryPath), "Zip [" + file + "] is missing entry [" + entry + "]");
      });
    }
  }

  private static void assertZipContainsDirectory(Path file, String entry, Integer mode) throws IOException {
    try (FileSystem zipFs = FileSystems.newFileSystem(file, Map.of("enablePosixFileAttributes", true))) {
      // Strip trailing slash for path lookup
      String pathEntry = entry.endsWith("/") ? entry.substring(0, entry.length() - 1) : entry;
      Path entryPath = zipFs.getPath(pathEntry);
      if (Files.notExists(entryPath)) {
        fail("ZIP [" + file + "] is missing directory [" + entry + "]");
      }

      assertTrue(Files.isDirectory(entryPath));
      if (mode != null) {
        Set<PosixFilePermission> expectedPerms = FileTools.toPosixPermissions(FileTools.toMode(mode));
        Set<PosixFilePermission> actualPerms = Files.getPosixFilePermissions(entryPath);
        assertEquals(actualPerms, expectedPerms);
      }
    }
  }

  private static void assertZipFileEquals(Path zipFile, String entry, Path original) throws IOException {
    try (ZipInputStream jis = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry zipEntry = jis.getNextEntry();
      while (zipEntry != null && !zipEntry.getName().equals(entry)) {
        zipEntry = jis.getNextEntry();
      }

      if (zipEntry == null) {
        fail("Zip [" + zipFile + "] is missing entry [" + entry + "]");
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      int length;
      while ((length = jis.read(buf)) != -1) {
        baos.write(buf, 0, length);
      }

      assertEquals(Files.readAllBytes(original), baos.toByteArray());
      assertEquals(zipEntry.getSize(), Files.size(original));
    }

    // ZIP doesn't work well with this right now. Maybe in JDK 1.9 or something
//    assertEquals(zipEntry.getCreationTime(), Files.getAttribute(original, "creationTime"));
  }

  @Test
  public void build() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zips"));

    Path file = projectDir.resolve("build/test/zips/test.zip");
    ZipBuilder builder = new ZipBuilder(file);
    int count = builder.fileSet(new FileSet(projectDir.resolve("src/main/java")))
                       .fileSet(new FileSet(projectDir.resolve("src/test/java")))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .directory(new Directory("test/directory", 0x755, "root", "root", null))
                       .build();
    assertTrue(Files.isReadable(file));
    assertZipContains(file, "org/lattejava/io/Copier.java", "org/lattejava/io/CopierTest.java",
        "org/lattejava/io/FileSet.java", "org/lattejava/io/FileTools.java");
    assertZipFileEquals(file, "org/lattejava/io/Copier.java", projectDir.resolve("src/main/java/org/lattejava/io/Copier.java"));
    assertZipContainsDirectory(file, "test/directory/", 0x755);
    assertZipContainsDirectory(file, "org/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/jar/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/tar/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/zip/", 0x755);
    assertEquals(count, 207);
  }

  @Test
  public void buildRequiredDirectoryFailure() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zips"));

    Path file = projectDir.resolve("build/test/zips/test.zip");
    ZipBuilder builder = new ZipBuilder(file);
    try {
      builder.fileSet(new FileSet(projectDir.resolve("src/main/java")))
             .fileSet(new FileSet(projectDir.resolve("src/test/java")))
             .fileSet(new FileSet(projectDir.resolve("doesNotExist")))
             .build();
      fail("Should have failed");
    } catch (IOException e) {
      // Expected
    }
  }

  @Test
  public void buildStrings() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zips"));

    Path file = projectDir.resolve("build/test/zips/test.zip");
    ZipBuilder builder = new ZipBuilder(file.toString());
    int count = builder.fileSet(projectDir.resolve("src/main/java").toString())
                       .fileSet(projectDir.resolve("src/test/java").toString())
                       .optionalFileSet("doesNotExist")
                       .build();
    assertTrue(Files.isReadable(file));
    assertZipContains(file, "org/lattejava/io/Copier.java", "org/lattejava/io/CopierTest.java",
        "org/lattejava/io/FileSet.java", "org/lattejava/io/FileTools.java");
    assertZipFileEquals(file, "org/lattejava/io/Copier.java", projectDir.resolve("src/main/java/org/lattejava/io/Copier.java"));
    assertZipContainsDirectory(file, "org/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/jar/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/tar/", 0x755);
    assertZipContainsDirectory(file, "org/lattejava/io/zip/", 0x755);
    assertEquals(count, 206);
  }

  @Test
  public void mode() throws Exception {
    Path file = projectDir.resolve("build/test/zips/test.zip");

    FileTools.prune(file.getParent());
    assertTrue(Files.notExists(file));

    ZipBuilder builder = new ZipBuilder(file.toString());
    builder.fileSet(new ArchiveFileSet(projectDir.resolve("src/main/java"), "foo", 0x755, null,
               null, null, null, null, List.of(), List.of()))
           .build();
    assertTrue(Files.isReadable(file));
    assertZipContains(file, "foo/org/lattejava/io/Copier.java", "foo/org/lattejava/io/FileSet.java");
    assertZipFileEquals(file, "foo/org/lattejava/io/Copier.java", projectDir.resolve("src/main/java/org/lattejava/io/Copier.java"));

    ZipTools.unzip(file, projectDir.resolve("build/test/zips/exploded"));
    assertEquals(Files.getPosixFilePermissions(projectDir.resolve("build/test/zips/exploded/foo/org/lattejava/io/Copier.java")),
        new HashSet<>(asList(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_READ)));
  }
}
