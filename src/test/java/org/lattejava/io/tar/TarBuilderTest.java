/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io.tar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.lattejava.io.ArchiveFileSet;
import org.lattejava.BaseUnitTest;
import org.lattejava.io.Directory;
import org.lattejava.io.FileSet;
import org.lattejava.io.FileTools;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the TarBuilder.
 * <p>
 * These tests build archives from the frozen fixture tree under {@code src/test/resources/archive-fixture} rather than
 * the live project source, so the exact entry counts never drift as the project changes.
 *
 * @author Brian Pontarelli
 */
public class TarBuilderTest extends BaseUnitTest {
  private static final Path mainFixture = Paths.get("src/test/resources/archive-fixture/main");

  private static final Path testFixture = Paths.get("src/test/resources/archive-fixture/test");

  private static void assertTarFileEquals(Path tarFile, String entry, Path original) throws IOException {
    InputStream is = Files.newInputStream(tarFile);
    if (tarFile.toString().endsWith(".gz")) {
      is = new GZIPInputStream(is);
    }

    try (TarArchiveInputStream tis = new TarArchiveInputStream(is)) {
      TarArchiveEntry tarArchiveEntry = tis.getNextTarEntry();
      while (tarArchiveEntry != null && !tarArchiveEntry.getName().equals(entry)) {
        tarArchiveEntry = tis.getNextTarEntry();
      }

      if (tarArchiveEntry == null) {
        fail("Tar [" + tarFile + "] is missing entry [" + entry + "]");
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      int length;
      while ((length = tis.read(buf)) != -1) {
        baos.write(buf, 0, length);
      }

      assertEquals(Files.readAllBytes(original), baos.toByteArray());
      assertEquals(tarArchiveEntry.getSize(), Files.size(original));
      assertEquals(tarArchiveEntry.getUserName(), Files.getOwner(original).getName());
      assertEquals(tarArchiveEntry.getGroupName(), Files.readAttributes(original, PosixFileAttributes.class).group().getName());
    }
  }

  private static void assertTarContainsDirectory(Path tarFile, String entry, Integer mode, String userName, String groupName) throws IOException {
    InputStream is = Files.newInputStream(tarFile);
    if (tarFile.toString().endsWith(".gz")) {
      is = new GZIPInputStream(is);
    }

    try (TarArchiveInputStream tis = new TarArchiveInputStream(is)) {
      TarArchiveEntry tarArchiveEntry = tis.getNextTarEntry();
      while (tarArchiveEntry != null && !tarArchiveEntry.getName().equals(entry)) {
        tarArchiveEntry = tis.getNextTarEntry();
      }

      if (tarArchiveEntry == null) {
        fail("Tar [" + tarFile + "] is missing entry [" + entry + "]");
      }

      assertTrue(tarArchiveEntry.isDirectory());
      if (mode != null) {
        assertEquals(tarArchiveEntry.getMode(), FileTools.toMode(mode));
      }
      if (userName != null) {
        assertEquals(tarArchiveEntry.getUserName(), userName);
      }
      if (groupName != null) {
        assertEquals(tarArchiveEntry.getGroupName(), groupName);
      }
    }
  }

  @Test
  public void build() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tars"));

    Path file = projectDir.resolve("build/test/tars/test.tar");
    TarBuilder builder = new TarBuilder(file);
    builder.storeGroupName = true;
    builder.storeUserName = true;
    int count = builder.fileSet(new FileSet(projectDir.resolve(mainFixture)))
                       .fileSet(new FileSet(projectDir.resolve(testFixture)))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .directory(new Directory("test/directory", 0x755, "root", "root", null))
                       .build();
    assertTrue(Files.isReadable(file));
    assertTarFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertTarFileEquals(file, "com/example/app/io/Reader.java", projectDir.resolve(mainFixture).resolve("com/example/app/io/Reader.java"));
    assertTarContainsDirectory(file, "com/", null, null, null);
    assertTarContainsDirectory(file, "com/example/", null, null, null);
    assertTarContainsDirectory(file, "com/example/app/", null, null, null);
    assertTarContainsDirectory(file, "test/directory/", 0x755, "root", "root");
    assertEquals(count, 14);
  }

  @Test
  public void buildArchiveFileSets() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tars"));

    Path file = projectDir.resolve("build/test/tars/test.tar");
    TarBuilder builder = new TarBuilder(file);
    builder.storeGroupName = true;
    builder.storeUserName = true;
    int count = builder.fileSet(new ArchiveFileSet(projectDir.resolve(mainFixture), "usr/local/main"))
                       .fileSet(new ArchiveFileSet(projectDir.resolve(testFixture), "usr/local/test"))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .directory(new Directory("test/directory", 0x755, "root", "root", null))
                       .build();
    assertTrue(Files.isReadable(file));
    assertTarFileEquals(file, "usr/local/main/com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertTarFileEquals(file, "usr/local/main/com/example/app/io/Reader.java", projectDir.resolve(mainFixture).resolve("com/example/app/io/Reader.java"));
    assertTarFileEquals(file, "usr/local/test/com/example/app/AppTest.java", projectDir.resolve(testFixture).resolve("com/example/app/AppTest.java"));
    assertTarContainsDirectory(file, "usr/local/main/com/", null, null, null);
    assertTarContainsDirectory(file, "usr/local/test/com/", null, null, null);
    assertTarContainsDirectory(file, "usr/local/main/com/example/", null, null, null);
    assertTarContainsDirectory(file, "usr/local/test/com/example/", null, null, null);
    assertTarContainsDirectory(file, "usr/local/main/com/example/app/", null, null, null);
    assertTarContainsDirectory(file, "usr/local/test/com/example/app/", null, null, null);
    assertTarContainsDirectory(file, "test/directory/", 0x755, "root", "root");
    assertEquals(count, 22);
  }

  @Test
  public void buildCompress() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tars"));

    Path file = projectDir.resolve("build/test/tars/test.tar.gz");
    TarBuilder builder = new TarBuilder(file);
    builder.storeGroupName = true;
    builder.storeUserName = true;
    builder.compress = true;
    int count = builder.fileSet(new FileSet(projectDir.resolve(mainFixture)))
                       .fileSet(new FileSet(projectDir.resolve(testFixture)))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .build();
    assertTrue(Files.isReadable(file));
    assertTarFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertTarFileEquals(file, "com/example/app/io/Reader.java", projectDir.resolve(mainFixture).resolve("com/example/app/io/Reader.java"));
    assertEquals(count, 13);
  }

  @Test
  public void buildRequiredDirectoryFailure() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tars"));

    Path file = projectDir.resolve("build/test/tars/test.tar");
    TarBuilder builder = new TarBuilder(file);
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
    FileTools.prune(projectDir.resolve("build/test/tars"));

    Path file = projectDir.resolve("build/test/tars/test.tar.gz");
    TarBuilder builder = new TarBuilder(file.toString());
    builder.storeGroupName = true;
    builder.storeUserName = true;
    int count = builder.fileSet(projectDir.resolve(mainFixture).toString())
                       .fileSet(projectDir.resolve(testFixture).toString())
                       .optionalFileSet("doesNotExist")
                       .build();
    assertTrue(Files.isReadable(file));
    assertTarFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertTarFileEquals(file, "com/example/app/io/Reader.java", projectDir.resolve(mainFixture).resolve("com/example/app/io/Reader.java"));
    assertEquals(count, 13);
  }
}
