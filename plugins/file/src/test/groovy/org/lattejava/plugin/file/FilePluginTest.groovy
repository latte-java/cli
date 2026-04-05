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
package org.lattejava.plugin.file

import org.lattejava.dep.domain.License
import org.lattejava.cli.domain.Project
import org.lattejava.domain.Version
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

import static org.testng.Assert.*

/**
 * Tests the FilePlugin class.
 *
 * @author Brian Pontarelli
 */
class FilePluginTest {
  public static Path projectDir

  Output output

  Project project

  FilePlugin plugin

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("build.savant"))) {
      projectDir = Paths.get("../file")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir, output)
    project.group = "org.lattejava.test"
    project.name = "file-test"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("MIT", null))

    plugin = new FilePlugin(project, new RuntimeConfiguration(), output)
  }

  @Test
  void append() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/append"))
    plugin.append(to: "build/test/append/target.txt") {
      fileSet(dir: "src/test/resources", includePatterns: [~/.+\.txt/])
    }

    byte[] buf = Files.readAllBytes(projectDir.resolve("build/test/append/target.txt"))
    assertEquals(new String(buf), "one\ntwo\n")
  }

  @Test
  void copyDirectoryToDirectoryWithPaths() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/copy"))
    plugin.copy(to: Paths.get("build/test/copy")) {
      fileSet(dir: Paths.get("src/main/groovy"))
    }

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/org/lattejava/plugin/file/FilePlugin.groovy")))
  }

  @Test
  void copyWithIncludePatterns() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/copy"))
    plugin.copy(to: Paths.get("build/test/copy")) {
      fileSet(dir: Paths.get("src/main/groovy"), includePatterns: [/.*\/file\/.*/])
    }

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/org/lattejava/plugin/file/FilePlugin.groovy")))
  }

  @Test
  void copyDirectoryToDirectoryWithStrings() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/copy"))
    plugin.copy(to: "build/test/copy") {
      fileSet(dir: "src/main/groovy")
    }

    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/copy/org/lattejava/plugin/file/FilePlugin.groovy")))
  }

  @Test
  void copyFile() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/copy"))
    plugin.copyFile(file: "src/test/resources/append1.txt", to: "build/test/copy/copy-file.txt")
    assertEquals(new String(Files.readAllBytes(projectDir.resolve("build/test/copy/copy-file.txt"))), "one\n")

    plugin.copyFile(file: "src/test/resources/append2.txt", to: "build/test/copy/copy-file.txt")
    assertEquals(new String(Files.readAllBytes(projectDir.resolve("build/test/copy/copy-file.txt"))), "two\n")
  }

  @Test
  void delete() throws Exception {
    FileTools.prune(projectDir.resolve("build/test"))
    assertFalse(Files.isDirectory(projectDir.resolve("build/test")))

    Files.createDirectories(projectDir.resolve("build/test"))
    Files.write(projectDir.resolve("build/test/file-3.0-{integration}.txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file2-1.0-{integration}.txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file3-1.0-{integration}.[second].txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file4-1.0.[second].txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file5.txt"), "Some text".getBytes())

    plugin.delete {
      fileSet(dir: "build/test", includePatterns: [~/.+\{integration}\.+/, ~/.+\[second].+/])
    }

    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/file-3.0-{integration}.txt")))
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/file2-1.0-{integration}.txt")))
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/file3-1.0-{integration}.[second].txt")))
    assertFalse(Files.isRegularFile(projectDir.resolve("build/test/file4-1.0.[second].txt")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/file5.txt")))
  }

  @Test
  void jarWithPaths() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jar"))
    plugin.jar(file: Paths.get("build/test/jar/test.jar")) {
      fileSet(dir: Paths.get("build/classes/main"))
    }

    assertJarContains(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class")
    assertJarFileEquals(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class", projectDir.resolve("build/classes/main/org/lattejava/plugin/file/FilePlugin.class"))
  }

  @Test
  void jarWithStrings() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jar"))
    plugin.jar(file: "build/test/jar/test.jar") {
      fileSet(dir: "build/classes/main")
    }

    assertJarContains(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class")
    assertJarFileEquals(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class", projectDir.resolve("build/classes/main/org/lattejava/plugin/file/FilePlugin.class"))
  }

  @Test
  void jarManifestFile() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jar"))
    plugin.jar(file: "build/test/jar/test.jar") {
      fileSet(dir: "build/classes/main")
      manifest(file: "src/test/resources/MANIFEST.MF")
    }

    assertJarContains(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class", "META-INF/MANIFEST.MF")
    assertJarFileEquals(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class", projectDir.resolve("build/classes/main/org/lattejava/plugin/file/FilePlugin.class"))
    assertJarManifest(projectDir.resolve("build/test/jar/test.jar"), new Manifest(Files.newInputStream(projectDir.resolve("src/test/resources/MANIFEST.MF"))))
  }

  @Test
  void jarManifestMap() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/jar"))
    plugin.jar(file: "build/test/jar/test.jar") {
      fileSet(dir: "build/classes/main")
      manifest(map: [
          "Specification-Version": "1.0",
          "Specification-Vendor" : "FooBar",
      ])
    }

    Manifest expected = new Manifest()
    expected.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
    expected.getMainAttributes().put(Attributes.Name.SPECIFICATION_VERSION, "1.0")
    expected.getMainAttributes().put(Attributes.Name.SPECIFICATION_VENDOR, "FooBar")

    assertJarContains(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class", "META-INF/MANIFEST.MF")
    assertJarFileEquals(projectDir.resolve("build/test/jar/test.jar"), "org/lattejava/plugin/file/FilePlugin.class", projectDir.resolve("build/classes/main/org/lattejava/plugin/file/FilePlugin.class"))
    assertJarManifest(projectDir.resolve("build/test/jar/test.jar"), expected)
  }

  @Test
  void mkdir() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/dir"))
    assertFalse(Files.isDirectory(projectDir.resolve("build/test/dir")))

    plugin.mkdir(dir: "build/test/dir")
    assertTrue(Files.isDirectory(projectDir.resolve("build/test/dir")))
  }

  @Test
  void rename() throws Exception {
    FileTools.prune(projectDir.resolve("build/test"))
    assertFalse(Files.isDirectory(projectDir.resolve("build/test")))

    Files.createDirectories(projectDir.resolve("build/test"))
    Files.write(projectDir.resolve("build/test/file-3.0-{integration}.txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file2-1.0-{integration}.txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file3-1.0-{integration}.[second].txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file4-1.0.[second].txt"), "Some text".getBytes())
    Files.write(projectDir.resolve("build/test/file5.txt"), "Some text".getBytes())

    plugin.rename {
      fileSet(dir: "build/test")
      filter(token: "-{integration}", value: "")
      filter(token: ".[second]", value: "")
    }

    assertFilesContents("build/test/file-3.0.txt", "Some text")
    assertFilesContents("build/test/file2-1.0.txt", "Some text")
    assertFilesContents("build/test/file3-1.0.txt", "Some text")
    assertFilesContents("build/test/file4-1.0.txt", "Some text")
    assertFilesContents("build/test/file5.txt", "Some text")
  }

  @Test
  void symlink() throws Exception {
    FileTools.prune(projectDir.resolve("build/test"))
    assertFalse(Files.isDirectory(projectDir.resolve("build/test")))

    plugin.symlink(link: "build/test/link", target: "src/main/groovy")
    assertTrue(Files.isSymbolicLink(projectDir.resolve("build/test/link")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/link/org/lattejava/plugin/file/FilePlugin.groovy")))
  }

  @Test
  void tarCompressed() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tar"))
    plugin.tar(file: Paths.get("build/test/tar/test.tar.gz"), compress: true) {
      fileSet(dir: Paths.get("build/classes/main"))
      tarFileSet(prefix: "testing123", dir: Paths.get("build/classes/test"))
    }

    Process process = "gunzip test.tar.gz".execute([], projectDir.resolve("build/test/tar").toFile())
    process.consumeProcessOutput()
    process.waitFor()

    assertEquals(process.exitValue(), 0)

    process = "tar -xvf test.tar".execute([], projectDir.resolve("build/test/tar").toFile())
    process.consumeProcessOutput()
    process.waitFor()

    assertEquals(process.exitValue(), 0)
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/tar/org/lattejava/plugin/file/FilePlugin.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/tar/testing123/org/lattejava/plugin/file/FilePluginTest.class")))
  }

  @Test
  void tarWithPaths() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tar"))
    plugin.tar(file: Paths.get("build/test/tar/test.tar")) {
      fileSet(dir: Paths.get("build/classes/main"))
      tarFileSet(prefix: "testing123", dir: Paths.get("build/classes/test"))
    }

    Process process = "tar -xvf test.tar".execute([], projectDir.resolve("build/test/tar").toFile())
    process.consumeProcessOutput()
    process.waitFor()

    assertEquals(process.exitValue(), 0)
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/tar/org/lattejava/plugin/file/FilePlugin.class")))
    assertTrue(Files.isReadable(projectDir.resolve("build/test/tar/org/lattejava/plugin/file/FilePlugin.class")))
    assertTrue(Files.isWritable(projectDir.resolve("build/test/tar/org/lattejava/plugin/file/FilePlugin.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/tar/testing123/org/lattejava/plugin/file/FilePluginTest.class")))
    assertTrue(Files.isReadable(projectDir.resolve("build/test/tar/testing123/org/lattejava/plugin/file/FilePluginTest.class")))
    assertTrue(Files.isWritable(projectDir.resolve("build/test/tar/testing123/org/lattejava/plugin/file/FilePluginTest.class")))
  }

  @Test
  void tarWithStrings() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/tar"))
    plugin.tar(file: "build/test/tar/test.tar") {
      fileSet(dir: "build/classes/main")
      tarFileSet(prefix: "testing123", dir: "build/classes/test")
    }

    Process process = "tar -xvf test.tar".execute([], projectDir.resolve("build/test/tar").toFile())
    process.consumeProcessOutput()
    process.waitFor()

    assertEquals(process.exitValue(), 0)
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/tar/org/lattejava/plugin/file/FilePlugin.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("build/test/tar/testing123/org/lattejava/plugin/file/FilePluginTest.class")))
  }

  @Test
  void zipWithPaths() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zip"))
    plugin.zip(file: Paths.get("build/test/zip/test.zip")) {
      fileSet(dir: Paths.get("build/classes/main"))
    }

    assertZipContains(projectDir.resolve("build/test/zip/test.zip"), "org/lattejava/plugin/file/FilePlugin.class")
    assertZipFileEquals(projectDir.resolve("build/test/zip/test.zip"), "org/lattejava/plugin/file/FilePlugin.class", projectDir.resolve("build/classes/main/org/lattejava/plugin/file/FilePlugin.class"))
  }

  @Test
  void zipWithStrings() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zip"))
    plugin.zip(file: "build/test/zip/test.zip") {
      fileSet(dir: "build/classes/main")
    }

    assertZipContains(projectDir.resolve("build/test/zip/test.zip"), "org/lattejava/plugin/file/FilePlugin.class")
    assertZipFileEquals(projectDir.resolve("build/test/zip/test.zip"), "org/lattejava/plugin/file/FilePlugin.class", projectDir.resolve("build/classes/main/org/lattejava/plugin/file/FilePlugin.class"))
  }

  @Test
  void zipWithExcludeAndPrefixes() throws Exception {
    FileTools.prune(projectDir.resolve("build/test/zip"))
    plugin.zip(file: "build/test/zip/test.zip") {
      zipFileSet(dir: "src", prefix: "foobar", excludePatterns: [~/.*\/file\/Copy.*/, ~/test\/.*/])
    }

    assertZipContains(projectDir.resolve("build/test/zip/test.zip"), "foobar/main/groovy/org/lattejava/plugin/file/FilePlugin.groovy")
    assertZipNotContains(projectDir.resolve("build/test/zip/test.zip"), "foobar/main/groovy/org/lattejava/plugin/file/CopyDelegate.groovy", "foobar/test/groovy/org/lattejava/plugin/file/FilePluginTest.groovy")
    assertZipFileEquals(projectDir.resolve("build/test/zip/test.zip"), "foobar/main/groovy/org/lattejava/plugin/file/FilePlugin.groovy", projectDir.resolve("src/main/groovy/org/lattejava/plugin/file/FilePlugin.groovy"))
  }

  @Test
  void unjar() {
    jarManifestFile()
    plugin.unjar(file: "build/test/jar/test.jar", to: "build/test/jar/exploded")

    assertFilesEqual("build/test/jar/exploded/org/lattejava/plugin/file/FilePlugin.class", "build/classes/main/org/lattejava/plugin/file/FilePlugin.class")
  }

  private static void assertFilesContents(String fileName, String contents) {
    assertEquals(new String(Files.readAllBytes(projectDir.resolve(fileName))), contents)
  }

  private static void assertFilesEqual(String file1, String file2) {
    String file1Contents = new String(Files.readAllBytes(projectDir.resolve(file1)))
    String file2Contents = new String(Files.readAllBytes(projectDir.resolve(file2)))
    assertEquals(file1Contents, file2Contents)
  }

  private static void assertJarContains(Path jarFile, String... entries) {
    JarFile jf = new JarFile(jarFile.toFile())
    entries.each({ entry -> assertNotNull(jf.getEntry(entry), "Jar [${jarFile}] is missing entry [${entry}]") })
    jf.close()
  }

  private static void assertJarFileEquals(Path jarFile, String entry, Path original) throws IOException {
    JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile))
    JarEntry jarEntry = jis.getNextJarEntry()
    while (jarEntry != null && !jarEntry.getName().equals(entry)) {
      jarEntry = jis.getNextJarEntry()
    }

    if (jarEntry == null) {
      fail("Jar [" + jarFile + "] is missing entry [" + entry + "]")
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    byte[] buf = new byte[1024]
    int length
    while ((length = jis.read(buf)) != -1) {
      baos.write(buf, 0, length)
    }

    assertEquals(Files.readAllBytes(original), baos.toByteArray())
    assertEquals(jarEntry.getSize(), Files.size(original))
    assertEquals(jarEntry.getCreationTime(), Files.getAttribute(original, "creationTime"))
    jis.close()
  }

  private static void assertJarManifest(Path jarFile, Manifest expected) throws IOException {
    JarFile jf = new JarFile(jarFile.toFile())
    Manifest actual = jf.getManifest()
    expected.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0.0")
    expected.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "org.lattejava.test.file-plugin-test")

    assertEquals(actual.getMainAttributes(), expected.getMainAttributes(), "Actual " + actual.getMainAttributes() + " expected " + expected.getMainAttributes())
    jf.close()
  }

  private static void assertZipContains(Path zipFile, String... entries) {
    ZipFile jf = new ZipFile(zipFile.toFile())
    entries.each({ entry -> assertNotNull(jf.getEntry(entry), "Zip [${zipFile}] is missing entry [${entry}]") })
    jf.close()
  }

  private static void assertZipNotContains(Path zipFile, String... entries) {
    ZipFile jf = new ZipFile(zipFile.toFile())
    entries.each({ entry -> assertNull(jf.getEntry(entry), "Zip [${zipFile}] is contains entry [${entry}] and shouldn't") })
    jf.close()
  }

  private static void assertZipFileEquals(Path zipFile, String entry, Path original) throws IOException {
    ZipInputStream jis = new ZipInputStream(Files.newInputStream(zipFile))
    ZipEntry zipEntry = jis.getNextEntry()
    while (zipEntry != null && !zipEntry.getName().equals(entry)) {
      zipEntry = jis.getNextEntry()
    }

    if (zipEntry == null) {
      fail("Zip [" + zipFile + "] is missing entry [" + entry + "]")
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    byte[] buf = new byte[1024]
    int length
    while ((length = jis.read(buf)) != -1) {
      baos.write(buf, 0, length)
    }

    assertEquals(Files.readAllBytes(original), baos.toByteArray())
    assertEquals(zipEntry.getSize(), Files.size(original))
    jis.close()
  }
}
