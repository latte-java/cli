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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the Copier.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class CopierTest extends BaseUnitTest {
  @Test
  public void copyEverything() throws Exception {
    Path toDir = BaseUnitTest.projectDir.resolve("build/test/copy");
    FileTools.prune(toDir);

    Copier copier = new Copier(BaseUnitTest.projectDir.resolve("build/test/copy"));
    copier.fileSet(BaseUnitTest.projectDir.resolve("src/main/java"))
          .copy();

    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/Copier.java")));
    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/FileTools.java")));
    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/FileSet.java")));
  }

  @Test
  public void copyFilters() throws Exception {
    Path toDir = BaseUnitTest.projectDir.resolve("build/test/copy");
    FileTools.prune(toDir);

    Copier copier = new Copier(BaseUnitTest.projectDir.resolve("build/test/copy"));
    copier.fileSet(new FileSet(BaseUnitTest.projectDir.resolve("src/test/java"), asList(Pattern.compile(".*/io/.*")), asList(Pattern.compile(".*FileSet.*"))))
          .filter("%TOKEN1%", "token1")
          .filter("%TOKEN4%", "token4")
          .filter("\n.*\\@Token5\\(\\w*\\)\n", " and ")
          .copy();

    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/CopierTest.java")));
    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/FileToolsTest.java")));
    assertFalse(Files.isRegularFile(toDir.resolve("org/savantbuild/io/FileSetTest.java")));

    assertEquals(new String(Files.readAllBytes(toDir.resolve("org/savantbuild/io/TestFilterFile.txt"))),
        "This file contains token1 and %TOKEN2%\n" +
            "It should be replaced with %TOKEN3% and token4\n" +
            "Also the next line and this line and this one should be one line");
  }

  @Test
  public void copyIncludePatterns() throws Exception {
    Path toDir = BaseUnitTest.projectDir.resolve("build/test/copy");
    FileTools.prune(toDir);

    Copier copier = new Copier(BaseUnitTest.projectDir.resolve("build/test/copy"));
    copier.fileSet(new FileSet(BaseUnitTest.projectDir.resolve("src/main/java"), asList(Pattern.compile(".*/io/.*")), asList(Pattern.compile(".*FileSet.*"))))
          .copy();

    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/Copier.java")));
    assertTrue(Files.isRegularFile(toDir.resolve("org/savantbuild/io/FileTools.java")));
    assertFalse(Files.isRegularFile(toDir.resolve("org/savantbuild/io/FileSet.java")));
  }
}
