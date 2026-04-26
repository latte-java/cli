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
package org.lattejava.dep;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * File utilities.
 *
 * @author Brian Pontarelli
 */
public class PathTools {
  /**
   * Creates a temporary file.
   *
   * @param prefix       The prefix for the temporary file.
   * @param suffix       The suffix for the temporary file.
   * @param deleteOnExit If the file should be deleted when the JVM exits.
   * @return The Path of the temporary file.
   * @throws IOException If the create fails.
   */
  public static Path createTempPath(String prefix, String suffix, boolean deleteOnExit) throws IOException {
    File file = File.createTempFile(prefix, suffix);
    if (deleteOnExit) {
      file.deleteOnExit();
    }

    return file.toPath();
  }

  /**
   * Prunes the given path. If the path is a directory, this deletes everything underneath it, but does not traverse
   * across symbolic links, it simply deletes the link. If the path is a file, it is deleted. If the path is a symbolic
   * link, it is unlinked.
   *
   * @param path The path to delete.
   * @throws IOException If the prune failed for any reason. This might indicate that the prune was only partially
   *                     successful.
   */
  public static void prune(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    if (Files.isSymbolicLink(path)) {
      Files.delete(path);
      return;
    }

    Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
