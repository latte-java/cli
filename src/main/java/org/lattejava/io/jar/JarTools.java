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
package org.lattejava.io.jar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Collection of JAR file tools.
 *
 * @author Brian Pontarelli
 */
public class JarTools {
  /**
   * Unzips a JAR file to a directory.
   *
   * @param file The JAR file to unjar.
   * @param to   The directory to unjar to.
   * @throws IOException If the unjar fails.
   */
  public static void unjar(Path file, Path to) throws IOException {
    if (Files.notExists(to)) {
      Files.createDirectories(to);
    }

    try (JarFile jarFile = new JarFile(file.toFile())) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        Path entryPath = to.resolve(entry.getName());
        if (!entry.isDirectory()) {
          if (Files.notExists(entryPath.getParent())) {
            Files.createDirectories(entryPath.getParent());
          }

          if (Files.isRegularFile(entryPath)) {
            if (Files.size(entryPath) == entry.getSize()) {
              continue;
            } else {
              Files.delete(entryPath);
            }
          }

          Files.createFile(entryPath);

          try (OutputStream os = Files.newOutputStream(entryPath)) {
            InputStream is = jarFile.getInputStream(entry);
            byte[] ba = new byte[1024];
            int read;
            while ((read = is.read(ba)) != -1) {
              if (read > 0) {
                os.write(ba, 0, read);
              }
            }
          }
        }
      }
    }
  }
}
