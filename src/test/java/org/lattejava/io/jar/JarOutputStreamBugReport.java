/*
 * Copyright (c) 2018, Inversoft Inc., All Rights Reserved
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

/**
 * @author Daniel DeGroff
 */
public class JarOutputStreamBugReport {
  public static void main(String[] args) throws IOException {

    // Create a test file to include in the jar.
    Files.deleteIfExists(Paths.get("./foo"));
    Path file = Files.createFile(Paths.get("./foo"));
    file.toFile().deleteOnExit();

    FileTime creationTime = (FileTime) Files.getAttribute(file, "creationTime");
    FileTime lastAccessTime = (FileTime) Files.getAttribute(file, "lastAccessTime");
    FileTime lastModifiedTime = Files.getLastModifiedTime(file);

    File jarFile = new File("testcase");
    jarFile.deleteOnExit();

    // 1. Passes, order is Ok.
    runTestCase(jarFile, file, entry -> {
      entry.setCreationTime(creationTime);
      entry.setLastAccessTime(lastAccessTime);

      // Calling setTime prior to setLastModifiedTime is Ok.
      entry.setTime(lastModifiedTime.toMillis());
      entry.setLastModifiedTime(lastModifiedTime);
    });

    // 2. Passes, omit the call to setTime
    runTestCase(jarFile, file, entry -> {
      entry.setCreationTime(creationTime);
      entry.setLastAccessTime(lastAccessTime);

      // Omitting the call to setTime is Ok.
      entry.setLastModifiedTime(lastModifiedTime);
    });

    // 3. Passes, omit setCreationTime and setLastAccessTime then order does not matter
    runTestCase(jarFile, file, entry -> {
      // Calling these two in either order is ok when we don't call setCreationTime and setLastAccessTime
      entry.setTime(lastModifiedTime.toMillis());
      entry.setLastModifiedTime(lastModifiedTime);
    });

    // 4. Passes, omit setCreationTime and setLastAccessTime then order does not matter
    runTestCase(jarFile, file, entry -> {
      // Calling these two in either order is ok when we don't call setCreationTime and setLastAccessTime
      entry.setLastModifiedTime(lastModifiedTime);
      entry.setTime(lastModifiedTime.toMillis());
    });

    // 5. Fails on OpenJDK 10 and 11, passes on Oracle 1.8
    runTestCase(jarFile, file, entry -> {
      entry.setCreationTime(creationTime);
      entry.setLastAccessTime(lastAccessTime);

      // Calling setLastModifiedTime prior to setTime when also calling setCreationTime and setLastAccessTime fails.
      entry.setLastModifiedTime(lastModifiedTime);
      entry.setTime(lastModifiedTime.toMillis());
    });
  }

  private static void runTestCase(File jarFile, Path file, Consumer<JarEntry> consumer) throws IOException {
    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile.toPath()), new Manifest())) {
      JarEntry entry = new JarEntry(file.toString());
      consumer.accept(entry);
      entry.setSize((Long) Files.getAttribute(file, "size"));
      jos.putNextEntry(entry);
      jos.flush();
      jos.closeEntry();
    }

    try {
      new JarFile(jarFile);
      System.out.println("Success!");
    } catch (ZipException e) {
      // Throws java.util.zip.ZipException: invalid CEN header (bad header size)
      System.out.println("Fail. " + e.getClass().getCanonicalName() + ": " + e.getMessage());
    }
  }
}
