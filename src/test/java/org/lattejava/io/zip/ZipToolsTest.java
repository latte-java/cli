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
