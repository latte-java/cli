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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File utilities.
 *
 * @author Brian Pontarelli
 */
public class FileTools {
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
   * Returns a Predicate that returns true if the Path has the given extension.
   *
   * @param extension The extension.
   * @return The Predicate.
   */
  public static Predicate<Path> extensionFilter(String extension) {
    return (path) -> path.toString().endsWith(extension);
  }

  /**
   * Returns a Function that maps a source path to a target path by changing the extension of the Path.
   *
   * @param original The original extension.
   * @param target   The target extension.
   * @return The Function.
   */
  public static Function<Path, Path> extensionMapper(String original, String target) {
    return (path) -> Paths.get(path.toString().replace(original, target));
  }

  /**
   * Determines the files that have been modified with respect to the given output directory.
   *
   * @param sourceDir The source directory to walk.
   * @param outputDir The output directory to compare against.
   * @param filter    A predicate that reduces the files that are compared. This should return true for files that are
   *                  compared and false for those that aren't.
   * @param mapper    A function that maps a source file to a target file.
   * @return The list of modified files.
   * @throws IllegalStateException If the code throws an IOException it is wrapped into an IllegalStateException.
   */
  public static List<Path> modifiedFiles(Path sourceDir, Path outputDir, Predicate<Path> filter, Function<Path, Path> mapper)
      throws IllegalStateException {
    if (!Files.isDirectory(sourceDir)) {
      return Collections.emptyList();
    }

    try (Stream<Path> stream = Files.walk(sourceDir)) {
      return stream.filter(filter)
                   .filter((path) -> {
                     Path subPath = path.subpath(sourceDir.getNameCount(), path.getNameCount());
                     Path mappedPath = mapper.apply(subPath);
                     Path outputPath = outputDir.resolve(mappedPath);
                     return isModified(path, outputPath);
                   })
                   .map((path) -> path.subpath(sourceDir.getNameCount(), path.getNameCount()))
                   .collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to determine which source files where changed", e);
    }
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

  /**
   * Converts the file permissions to a hex permission value.
   *
   * @param permissions The set of POSIX file permissions.
   * @return The hex value.
   */
  public static Integer toHexMode(Set<PosixFilePermission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return 0x644;
    }

    int mode = 0x000;
    for (PosixFilePermission permission : permissions) {
      switch (permission) {
        case GROUP_EXECUTE:
          mode |= 0x010;
          break;
        case GROUP_READ:
          mode |= 0x040;
          break;
        case GROUP_WRITE:
          mode |= 0x020;
          break;
        case OTHERS_EXECUTE:
          mode |= 0x001;
          break;
        case OTHERS_READ:
          mode |= 0x004;
          break;
        case OTHERS_WRITE:
          mode |= 0x002;
          break;
        case OWNER_EXECUTE:
          mode |= 0x100;
          break;
        case OWNER_READ:
          mode |= 0x400;
          break;
        case OWNER_WRITE:
          mode |= 0x200;
          break;
      }
    }

    return mode;
  }

  /**
   * <p>
   * Converts the file permissions of this FileInfo to a POSIX bit mapped mode. The bit map looks like this:
   * </p>
   * <pre>
   *   1_000_000_001_000_000
   * </pre>
   * <p>
   * The first bit is always set. The next three bits are the set UID bits, the next 3 bits are the set GID bits. The
   * next three bits are the owner permissions (read, write, execute), then the group permissions and finally the user
   * permissions.
   * </p>
   *
   * @param permissions The POSIX file permissions.
   * @return The POSIX mode bit map as an integer.
   */
  public static int toMode(Collection<PosixFilePermission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return 0b1_000_000_110_100_100;
    }

    int mode = 0b1_000_000_000_000_000;
    for (PosixFilePermission permission : permissions) {
      switch (permission) {
        case GROUP_EXECUTE:
          mode |= 0b001_000;
          break;
        case GROUP_READ:
          mode |= 0b100_000;
          break;
        case GROUP_WRITE:
          mode |= 0b010_000;
          break;
        case OTHERS_EXECUTE:
          mode |= 0b001;
          break;
        case OTHERS_READ:
          mode |= 0b100;
          break;
        case OTHERS_WRITE:
          mode |= 0b010;
          break;
        case OWNER_EXECUTE:
          mode |= 0b001_000_000;
          break;
        case OWNER_READ:
          mode |= 0b100_000_000;
          break;
        case OWNER_WRITE:
          mode |= 0b010_000_000;
          break;
      }
    }

    return mode;
  }

  /**
   * Converts a hex value to a POSIX mode. This allows you to use a hex lik 0x777 to represent rwx-rwx-rwx.
   *
   * @param hex The hex mode.
   * @return The POSIX mode.
   */
  public static int toMode(int hex) {
    int unixMode = 0b1_000_000_000_000_000;
    unixMode += (hex & 0b111);
    hex >>= 4; // Line up the next 3 bits
    unixMode += ((hex & 0b111) << 3);
    hex >>= 4; // Line up the final 3 bits
    unixMode += ((hex & 0b111) << 6);
    return unixMode;
  }

  /**
   * Converts the object to a Path.
   *
   * @param object The object.
   * @return The object as a path.
   */
  public static Path toPath(Object object) {
    if (object == null) {
      return null;
    }

    if (object instanceof Path) {
      return (Path) object;
    }

    return Paths.get(object.toString());
  }

  /**
   * <p>
   * Converts the POSIX bit mapped file mode integer to a set of PosixFilePermissions. The bit map looks like this:
   * </p>
   * <pre>
   *   1_000_000_001_000_000
   * </pre>
   * <p>
   * The first bit is always set. The next three bits are the set UID bits, the next 3 bits are the set GID bits. The
   * next three bits are the owner permissions (read, write, execute), then the group permissions and finally the user
   * permissions.
   * </p>
   *
   * @param mode The POSIX bit mapped permissions.
   * @return The POSIX mode bit map as an integer.
   */
  public static Set<PosixFilePermission> toPosixPermissions(int mode) {
    Set<PosixFilePermission> permissions = new HashSet<>();
    if ((mode & 0b001_000) == 0b001_000) {
      permissions.add(PosixFilePermission.GROUP_EXECUTE);
    }
    if ((mode & 0b100_000) == 0b100_000) {
      permissions.add(PosixFilePermission.GROUP_READ);
    }
    if ((mode & 0b010_000) == 0b010_000) {
      permissions.add(PosixFilePermission.GROUP_WRITE);
    }

    if ((mode & 0b001) == 0b001) {
      permissions.add(PosixFilePermission.OTHERS_EXECUTE);
    }
    if ((mode & 0b100) == 0b100) {
      permissions.add(PosixFilePermission.OTHERS_READ);
    }
    if ((mode & 0b010) == 0b010) {
      permissions.add(PosixFilePermission.OTHERS_WRITE);
    }

    if ((mode & 0b001_000_000) == 0b001_000_000) {
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
    }
    if ((mode & 0b100_000_000) == 0b100_000_000) {
      permissions.add(PosixFilePermission.OWNER_READ);
    }
    if ((mode & 0b010_000_000) == 0b010_000_000) {
      permissions.add(PosixFilePermission.OWNER_WRITE);
    }

    return permissions;
  }

  /**
   * Updates the last modified timestamp of each of the given Paths. This effectively "touches" each Path.
   *
   * @param paths The Paths to touch.
   * @throws IOException If the update fails.
   */
  public static void touch(Path... paths) throws IOException {
    for (Path path : paths) {
      Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
    }
  }

  /**
   * Determines if the given path is modified when compared to the same path in the given outputDir.
   *
   * @param sourcePath The source path.
   * @param outputPath The output path.
   * @return True if the sourcePath is modified when compared to the outputPath, false it if isn't.
   * @throws IllegalStateException If the check throws an IOException it is wrapped into an IllegalStateException.
   */
  private static boolean isModified(Path sourcePath, Path outputPath) {
    try {
      return !Files.isRegularFile(outputPath) || Files.getLastModifiedTime(outputPath).toMillis() < Files.getLastModifiedTime(sourcePath).toMillis();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
