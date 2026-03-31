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
package org.lattejava.io.tar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.regex.Pattern;

import org.lattejava.io.ArchiveFileSet;
import org.lattejava.BaseUnitTest;
import org.lattejava.io.Directory;
import org.lattejava.io.FileTools;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

/**
 * Tests the Tar tools.
 *
 * @author Brian Pontarelli
 */
public class TarToolsTest extends BaseUnitTest {
  @Test
  public void untar() throws Exception {
    FileTools.prune(projectDir.resolve("build/test"));
    Path testFile = projectDir.resolve("build/test/test.tar");
    TarBuilder builder = new TarBuilder(testFile);
    builder.storeGroupName = true;
    builder.storeUserName = true;
    builder.fileSet(projectDir.resolve("src/main/java"))
           .fileSet(new ArchiveFileSet(projectDir.resolve("src/test/java"), "test-files", 0x755, null, null, null, null, null, asList(Pattern.compile(".*/io/.*")), asList()))
           .directory(new Directory("test-directory/nested/dir", 0x777, null, null, null))
           .build();

    Path untarDir = projectDir.resolve("build/test/untar");
    TarTools.untar(testFile, untarDir, true, true);

    assertFilesEquals(projectDir.resolve("src/main/java/org/savantbuild/io/ArchiveFileSet.java"), untarDir.resolve("org/savantbuild/io/ArchiveFileSet.java"), null);
    assertFilesEquals(projectDir.resolve("src/test/java/org/savantbuild/io/ArchiveFileSetTest.java"), untarDir.resolve("test-files/org/savantbuild/io/ArchiveFileSetTest.java"), 0x755);
    assertDirectory(untarDir.resolve("test-directory/nested/dir"), 0x777);

    // Do it again and ensure things don't blow up
    TarTools.untar(testFile, untarDir, true, true);
  }

  @Test
  public void untar_compress() throws Exception {
    FileTools.prune(projectDir.resolve("build/test"));
    Path testFile = projectDir.resolve("build/test/test.tar.gz");
    TarBuilder builder = new TarBuilder(testFile);
    builder.compress = true;
    builder.fileSet(projectDir.resolve("src/main/java"));
    builder.build();

    Path untarDir = projectDir.resolve("build/test/untar");
    TarTools.untar(testFile, untarDir, false, false);
    Files.walk(untarDir).forEach((file) -> {
      if (Files.isDirectory(file)) {
        return;
      }

      Path source = projectDir.resolve("src/main/java").resolve(untarDir.relativize(file));
      try {
        assertEquals(Files.readAllBytes(file), Files.readAllBytes(source), "Files aren't equal [" + source + "] and [" + file + "]");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    // Do it again and ensure things don't blow up
    TarTools.untar(testFile, untarDir, false, false);
  }

  private void assertDirectory(Path dir, Integer mode) throws IOException {
    assertEquals(FileTools.toMode(Files.getPosixFilePermissions(dir)), FileTools.toMode(mode));
  }

  private void assertFilesEquals(Path expected, Path actual, Integer mode) throws IOException {
    assertEquals(Files.readAllBytes(actual), Files.readAllBytes(expected), "Files aren't equal [" + actual + "] and [" + expected + "]");

    String actualUserName = Files.getOwner(actual).getName();
    String expectedUserName = Files.getOwner(expected).getName();
    assertEquals(actualUserName, expectedUserName);

    String actualGroupName = Files.readAttributes(actual, PosixFileAttributes.class).group().getName();
    String expectedGroupName = Files.readAttributes(expected, PosixFileAttributes.class).group().getName();
    assertEquals(actualGroupName, expectedGroupName);

    if (mode != null) {
      assertEquals(FileTools.toMode(Files.getPosixFilePermissions(actual)), FileTools.toMode(mode));
    } else {
      int actualMode = FileTools.toMode(Files.getPosixFilePermissions(actual));
      int expectedMode = FileTools.toMode(Files.getPosixFilePermissions(expected));
      assertEquals(actualMode, expectedMode);
    }
  }
}
