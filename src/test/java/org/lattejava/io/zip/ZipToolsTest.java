/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lattejava.BaseUnitTest;
import org.lattejava.io.FileTools;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the Zip tools.
 *
 * @author Brian Pontarelli
 */
public class ZipToolsTest extends BaseUnitTest {
  @Test
  public void unzip() throws Exception {
    FileTools.prune(BaseUnitTest.projectDir.resolve("build/test"));
    Path testFile = BaseUnitTest.projectDir.resolve("build/test/test.zip");
    ZipBuilder builder = new ZipBuilder(testFile);
    builder.fileSet(BaseUnitTest.projectDir.resolve("src/main/java"));
    builder.build();

    Path unzipDir = BaseUnitTest.projectDir.resolve("build/test/unzip");
    ZipTools.unzip(testFile, unzipDir);
    Files.walk(unzipDir).forEach((file) -> {
      if (Files.isDirectory(file)) {
        return;
      }

      Path source = BaseUnitTest.projectDir.resolve("src/main/java").resolve(unzipDir.relativize(file));
      try {
        assertEquals(Files.readAllBytes(source), Files.readAllBytes(file), "Files aren't equal [" + source + "] and [" + file + "]");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    // Do it again and ensure things don't blow up
    ZipTools.unzip(testFile, unzipDir);
  }
}
