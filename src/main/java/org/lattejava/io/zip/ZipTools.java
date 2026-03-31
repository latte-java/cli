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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

/**
 * Collection of ZIP file tools.
 *
 * @author Brian Pontarelli
 */
public class ZipTools {
  /**
   * Unzips a ZIP file to a directory.
   *
   * @param file The ZIP file to unzip.
   * @param to   The directory to unzip to.
   * @throws IOException If the unzip fails.
   */
  public static void unzip(Path file, Path to) throws IOException {
    if (Files.notExists(to)) {
      Files.createDirectories(to);
    }

    try (FileSystem zipFs = FileSystems.newFileSystem(file, Map.of("enablePosixFileAttributes", true))) {
      Path root = zipFs.getPath("/");
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path zipEntry, BasicFileAttributes attrs) throws IOException {
          Path relativePath = root.relativize(zipEntry);
          Path entryPath = to.resolve(relativePath.toString());

          if (Files.notExists(entryPath.getParent())) {
            Files.createDirectories(entryPath.getParent());
          }

          if (Files.isRegularFile(entryPath)) {
            if (Files.size(entryPath) == attrs.size()) {
              return FileVisitResult.CONTINUE;
            } else {
              Files.delete(entryPath);
            }
          }

          Files.copy(zipEntry, entryPath);

          try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(zipEntry);
            if (!permissions.isEmpty()) {
              Files.setPosixFilePermissions(entryPath, permissions);
            }
          } catch (UnsupportedOperationException ignored) {
            // No POSIX permissions stored in this entry
          }

          return FileVisitResult.CONTINUE;
        }
      });
    }
  }
}
