/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io.zip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * <p>
 * These tests build archives from the frozen fixture tree under {@code src/test/resources/archive-fixture} rather than
 * the live project source, so the exact entry counts never drift as the project changes.
 *
 * @author Brian Pontarelli
 */
public class ZipBuilderTest extends BaseUnitTest {
  private static final Path mainFixture = Paths.get("src/test/resources/archive-fixture/main");

  private static final Path testFixture = Paths.get("src/test/resources/archive-fixture/test");

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
    int count = builder.fileSet(new FileSet(projectDir.resolve(mainFixture)))
                       .fileSet(new FileSet(projectDir.resolve(testFixture)))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .directory(new Directory("test/directory", 0x755, "root", "root", null))
                       .build();
    assertTrue(Files.isReadable(file));
    assertZipContains(file, "com/example/app/App.java", "com/example/app/AppTest.java",
        "com/example/app/io/Reader.java", "com/example/app/Util.java");
    assertZipFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertZipContainsDirectory(file, "test/directory/", 0x755);
    assertZipContainsDirectory(file, "com/", 0x755);
    assertZipContainsDirectory(file, "com/example/", 0x755);
    assertZipContainsDirectory(file, "com/example/app/", 0x755);
    assertZipContainsDirectory(file, "com/example/app/io/", 0x755);
    assertZipContainsDirectory(file, "com/example/app/io/jar/", 0x755);
    assertEquals(count, 14);
  }

  @Test
  public void buildRequiredDirectoryFailure() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zips"));

    Path file = projectDir.resolve("build/test/zips/test.zip");
    ZipBuilder builder = new ZipBuilder(file);
    try {
      builder.fileSet(new FileSet(projectDir.resolve(mainFixture)))
             .fileSet(new FileSet(projectDir.resolve(testFixture)))
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
    int count = builder.fileSet(projectDir.resolve(mainFixture).toString())
                       .fileSet(projectDir.resolve(testFixture).toString())
                       .optionalFileSet("doesNotExist")
                       .build();
    assertTrue(Files.isReadable(file));
    assertZipContains(file, "com/example/app/App.java", "com/example/app/AppTest.java",
        "com/example/app/io/Reader.java", "com/example/app/Util.java");
    assertZipFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertZipContainsDirectory(file, "com/", 0x755);
    assertZipContainsDirectory(file, "com/example/", 0x755);
    assertZipContainsDirectory(file, "com/example/app/", 0x755);
    assertZipContainsDirectory(file, "com/example/app/io/", 0x755);
    assertZipContainsDirectory(file, "com/example/app/io/jar/", 0x755);
    assertEquals(count, 13);
  }

  @Test
  public void mode() throws Exception {
    Path file = projectDir.resolve("build/test/zips/test.zip");

    FileTools.prune(file.getParent());
    assertTrue(Files.notExists(file));

    ZipBuilder builder = new ZipBuilder(file.toString());
    builder.fileSet(new ArchiveFileSet(projectDir.resolve(mainFixture), "foo", 0x755, null,
               null, null, null, null, List.of(), List.of()))
           .build();
    assertTrue(Files.isReadable(file));
    assertZipContains(file, "foo/com/example/app/App.java", "foo/com/example/app/io/Reader.java");
    assertZipFileEquals(file, "foo/com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));

    ZipTools.unzip(file, projectDir.resolve("build/test/zips/exploded"));
    assertEquals(Files.getPosixFilePermissions(projectDir.resolve("build/test/zips/exploded/foo/com/example/app/App.java")),
        new HashSet<>(asList(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_READ)));
  }
}
