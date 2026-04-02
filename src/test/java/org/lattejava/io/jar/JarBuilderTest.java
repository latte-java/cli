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
package org.lattejava.io.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import org.lattejava.BaseUnitTest;
import org.lattejava.io.Directory;
import org.lattejava.io.FileSet;
import org.lattejava.io.FileTools;
import org.testng.annotations.Test;

import static java.util.Arrays.stream;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the JarBuilder.
 *
 * @author Brian Pontarelli
 */
public class JarBuilderTest extends BaseUnitTest {
  private static void assertJarContains(JarFile jarFile, String... entries) throws IOException {
    stream(entries).forEach((entry) -> assertNotNull(jarFile.getEntry(entry), "Jar [" + jarFile + "] is missing entry [" + entry + "]"));
    jarFile.close();
  }

  private static void assertJarContainsDirectories(Path file, String... directories) throws IOException {
    JarFile jarFile = new JarFile(file.toFile());
    for (String directory : directories) {
      JarEntry jarEntry = jarFile.getJarEntry(directory);
      if (jarEntry == null) {
        fail("JAR [" + file + "] is missing directory [" + directory + "]");
      }

      assertTrue(jarEntry.isDirectory(), "Jar entry [" + directory + "] is not a directory");
    }

    jarFile.close();
  }

  private static void assertJarFileEquals(Path jarFile, String entry, Path original) throws IOException {
    try (JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile))) {
      JarEntry jarEntry = jis.getNextJarEntry();
      while (jarEntry != null && !jarEntry.getName().equals(entry)) {
        jarEntry = jis.getNextJarEntry();
      }

      if (jarEntry == null) {
        fail("Jar [" + jarFile + "] is missing entry [" + entry + "]");
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];
      int length;
      while ((length = jis.read(buf)) != -1) {
        baos.write(buf, 0, length);
      }

      assertEquals(Files.readAllBytes(original), baos.toByteArray());
      assertEquals(jarEntry.getSize(), Files.size(original));
      assertEquals(jarEntry.getCreationTime(), Files.getAttribute(original, "creationTime"));
    }
  }

  @Test
  public void build() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jars"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file);
    int count = builder.fileSet(new FileSet(projectDir.resolve("src/main/java")))
                       .fileSet(new FileSet(projectDir.resolve("src/test/java")))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .directory(new Directory("test/directory", 0x755, "root", "root", null))
                       .build();
    assertTrue(Files.isReadable(file));
    assertJarContains(new JarFile(file.toFile()), "org/lattejava/io/Copier.java", "org/lattejava/io/CopierTest.java",
        "org/lattejava/io/FileSet.java", "org/lattejava/io/FileTools.java");
    assertJarFileEquals(file, "org/lattejava/io/Copier.java", projectDir.resolve("src/main/java/org/lattejava/io/Copier.java"));
    assertJarContainsDirectories(file, "META-INF/", "test/directory/", "org/", "org/lattejava/", "org/lattejava/io/",
        "org/lattejava/io/jar/", "org/lattejava/io/tar/", "org/lattejava/io/zip/");
    assertEquals(count, 194);
  }

  @Test
  public void buildRequiredDirectoryFailure() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jars"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file);
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
    FileTools.prune(projectDir.resolve("build/test/jars"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file.toString());
    int count = builder.fileSet(projectDir.resolve("src/main/java").toString())
                       .fileSet(projectDir.resolve("src/test/java").toString())
                       .optionalFileSet("doesNotExist")
                       .build();
    assertTrue(Files.isReadable(file));
    assertJarContains(new JarFile(file.toFile()), "org/lattejava/io/Copier.java", "org/lattejava/io/CopierTest.java",
        "org/lattejava/io/FileSet.java", "org/lattejava/io/FileTools.java");
    assertJarFileEquals(file, "org/lattejava/io/Copier.java", projectDir.resolve("src/main/java/org/lattejava/io/Copier.java"));
    assertJarContainsDirectories(file, "META-INF/", "org/", "org/lattejava/", "org/lattejava/io/",
        "org/lattejava/io/jar/", "org/lattejava/io/tar/", "org/lattejava/io/zip/");
    assertEquals(count, 193);
  }

  @Test
  public void build_existingMETA_INF() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jars"));
    FileTools.prune(projectDir.resolve("build/test/resources/META-INF"));

    // create a META-INF directory with one file.
    Files.createDirectories(projectDir.resolve("build/test/resources/META-INF"));
    Files.createFile(projectDir.resolve("build/test/resources/META-INF/information.txt"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file);
    int count = builder.fileSet(new FileSet(projectDir.resolve("src/main/java")))
                       .fileSet(new FileSet(projectDir.resolve("src/test/java")))
                       .fileSet(new FileSet(projectDir.resolve("build/test/resources")))
                       .build();
    assertTrue(Files.isReadable(file));
    assertJarContains(new JarFile(file.toFile()), "org/lattejava/io/Copier.java", "org/lattejava/io/CopierTest.java",
        "org/lattejava/io/FileSet.java", "org/lattejava/io/FileTools.java", "META-INF/information.txt");
    assertJarFileEquals(file, "org/lattejava/io/Copier.java", projectDir.resolve("src/main/java/org/lattejava/io/Copier.java"));
    assertJarContainsDirectories(file, "META-INF/", "org/", "org/lattejava/", "org/lattejava/io/",
        "org/lattejava/io/jar/", "org/lattejava/io/tar/", "org/lattejava/io/zip/");
    assertEquals(count, 194);
  }
}
