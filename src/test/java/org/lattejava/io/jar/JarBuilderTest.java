/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io.jar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * <p>
 * These tests build archives from the frozen fixture tree under {@code src/test/resources/archive-fixture} rather than
 * the live project source, so the exact entry counts never drift as the project changes.
 *
 * @author Brian Pontarelli
 */
public class JarBuilderTest extends BaseUnitTest {
  private static final Path mainFixture = Paths.get("src/test/resources/archive-fixture/main");

  private static final Path testFixture = Paths.get("src/test/resources/archive-fixture/test");

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
    int count = builder.fileSet(new FileSet(projectDir.resolve(mainFixture)))
                       .fileSet(new FileSet(projectDir.resolve(testFixture)))
                       .optionalFileSet(new FileSet(projectDir.resolve("doesNotExist")))
                       .directory(new Directory("test/directory", 0x755, "root", "root", null))
                       .build();
    assertTrue(Files.isReadable(file));
    assertJarContains(new JarFile(file.toFile()), "com/example/app/App.java", "com/example/app/AppTest.java",
        "com/example/app/io/Reader.java", "com/example/app/Util.java");
    assertJarFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertJarContainsDirectories(file, "META-INF/", "test/directory/", "com/", "com/example/", "com/example/app/",
        "com/example/app/io/", "com/example/app/io/jar/");
    assertEquals(count, 15);
  }

  @Test
  public void buildRequiredDirectoryFailure() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jars"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file);
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
    FileTools.prune(projectDir.resolve("build/test/jars"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file.toString());
    int count = builder.fileSet(projectDir.resolve(mainFixture).toString())
                       .fileSet(projectDir.resolve(testFixture).toString())
                       .optionalFileSet("doesNotExist")
                       .build();
    assertTrue(Files.isReadable(file));
    assertJarContains(new JarFile(file.toFile()), "com/example/app/App.java", "com/example/app/AppTest.java",
        "com/example/app/io/Reader.java", "com/example/app/Util.java");
    assertJarFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertJarContainsDirectories(file, "META-INF/", "com/", "com/example/", "com/example/app/",
        "com/example/app/io/", "com/example/app/io/jar/");
    assertEquals(count, 14);
  }

  @Test
  public void build_existingMETA_INF() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jars"));
    FileTools.prune(projectDir.resolve("build/test/resources"));

    // create a META-INF directory with one file.
    Files.createDirectories(projectDir.resolve("build/test/resources/META-INF"));
    Files.createFile(projectDir.resolve("build/test/resources/META-INF/information.txt"));

    Path file = projectDir.resolve("build/test/jars/test.jar");
    JarBuilder builder = new JarBuilder(file);
    int count = builder.fileSet(new FileSet(projectDir.resolve(mainFixture)))
                       .fileSet(new FileSet(projectDir.resolve(testFixture)))
                       .fileSet(new FileSet(projectDir.resolve("build/test/resources")))
                       .build();
    assertTrue(Files.isReadable(file));
    assertJarContains(new JarFile(file.toFile()), "com/example/app/App.java", "com/example/app/AppTest.java",
        "com/example/app/io/Reader.java", "com/example/app/Util.java", "META-INF/information.txt");
    assertJarFileEquals(file, "com/example/app/App.java", projectDir.resolve(mainFixture).resolve("com/example/app/App.java"));
    assertJarContainsDirectories(file, "META-INF/", "com/", "com/example/", "com/example/app/",
        "com/example/app/io/", "com/example/app/io/jar/");
    assertEquals(count, 15);
  }
}
