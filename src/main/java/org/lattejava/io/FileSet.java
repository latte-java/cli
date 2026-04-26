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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * A FileSet represents a set of files within a directory. Currently, this only models all of the files contained in a
 * directory and all sub-directories below it.
 *
 * @author Brian Pontarelli
 */
public class FileSet {
  public static final Set<String> REQUIRED_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(asList("dir")));

  public static final Set<String> VALID_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(asList("dir", "includePatterns", "excludePatterns")));

  public final Path directory;

  public final Set<Pattern> excludePatterns = new HashSet<>();

  public final Set<Pattern> includePatterns = new HashSet<>();

  /**
   * Constructs a new FileSet.
   *
   * @param directory The directory of the FileSet.
   */
  public FileSet(Path directory) {
    this(directory, null, null);
  }

  /**
   * Constructs a new FileSet. The directory is required but everything else is optional.
   *
   * @param directory       The directory of the FileSet.
   * @param includePatterns (Optional) A list of regular expression Pattern objects that list the files to include.
   * @param excludePatterns (Optional) A list of regular expression Pattern objects that list the files to exclude.
   */
  public FileSet(Path directory, Collection<Pattern> includePatterns, Collection<Pattern> excludePatterns) {
    this.directory = directory;
    if (includePatterns != null) {
      this.includePatterns.addAll(includePatterns);
    }
    if (excludePatterns != null) {
      this.excludePatterns.addAll(excludePatterns);
    }
  }

  /**
   * Determines if the attributes given can be used to construct a FileSet.
   *
   * @param attributes The attributes.
   * @return Null if the attributes are valid, an error message describing why they aren't valid.
   */
  public static String attributesValid(Map<String, Object> attributes) {
    StringBuilder build = new StringBuilder();
    if (!attributes.keySet().containsAll(REQUIRED_ATTRIBUTES)) {
      build.append("Missing required attributes ").append(REQUIRED_ATTRIBUTES).append(" for a FileSet\n");
    }

    Set<String> invalidAttributes = attributes.keySet().stream().filter((attr) -> !VALID_ATTRIBUTES.contains(attr)).collect(Collectors.toSet());
    if (invalidAttributes.size() > 0) {
      build.append("Invalid attributes ").append(invalidAttributes).append(" for a FileSet\n");
    }

    if (attributes.containsKey("includePatterns") && !(attributes.get("includePatterns") instanceof Collection)) {
      build.append("The [includePatterns] attribute for a FileSet must be a Collection of some kind");
    }

    if (attributes.containsKey("excludePatterns") && !(attributes.get("excludePatterns") instanceof Collection)) {
      build.append("The [excludePatterns] attribute for a FileSet must be a Collection of some kind");
    }

    if (build.length() > 0) {
      return build.toString();
    }

    return null;
  }

  /**
   * Constructs a FileSet from a Map of attributes.
   *
   * @param dir        The directory for the FileSet.
   * @param attributes The attributes.
   * @return The FileSet.
   */
  public static FileSet fromAttributes(Path dir, Map<String, Object> attributes) {
    return new FileSet(dir)
        .withExcludePatterns(Tools.toPatterns((List) attributes.get("excludePatterns")))
        .withIncludePatterns(Tools.toPatterns((List) attributes.get("includePatterns")));
  }

  /**
   * Builds the set of directories that all of the files in this FileSet are contained within.
   *
   * @return The directories.
   * @throws IOException If the build fails.
   */
  public Set<Directory> toDirectories() throws IOException {
    List<FileInfo> infos = toFileInfos();
    Set<Directory> directories = new TreeSet<>();
    for (FileInfo info : infos) {
      Path relativeDir = info.relative.getParent();
      Path originDir = info.origin.getParent();
      while (relativeDir != null) {
        Integer mode = FileTools.toHexMode(Files.getPosixFilePermissions(originDir));
        String userName = Files.getOwner(originDir).getName();
        String groupName = Files.readAttributes(originDir, PosixFileAttributes.class).group().getName();
        FileTime lastModifiedTime = Files.getLastModifiedTime(originDir);
        directories.add(new Directory(relativeDir.toString(), mode, userName, groupName, lastModifiedTime));

        relativeDir = relativeDir.getParent();
        originDir = originDir.getParent();
      }
    }

    return directories;
  }

  /**
   * Converts this FileSet to a list of FileInfo objects. The info objects contain the origin Path and a relative Path.
   * They also include additional information about the file.
   *
   * @return A List of FileInfo objects for this FileSet.
   * @throws IOException If the directory traversal fails.
   */
  public List<FileInfo> toFileInfos() throws IOException {
    List<FileInfo> results = new ArrayList<>();
    Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path relativePath = file.subpath(directory.getNameCount(), file.getNameCount());
        FileInfo info = new FileInfo(file, relativePath);
        info.creationTime = (FileTime) Files.getAttribute(file, "creationTime");
        info.groupName = Files.readAttributes(file, PosixFileAttributes.class).group().getName();
        info.userName = Files.getOwner(file).getName();
        info.lastAccessTime = (FileTime) Files.getAttribute(file, "lastAccessTime");
        info.lastModifiedTime = Files.getLastModifiedTime(file);
        info.size = (Long) Files.getAttribute(file, "size");
        info.permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
        results.add(info);
        return FileVisitResult.CONTINUE;
      }
    });

    return results.stream().filter(this::includeFileInfo).sorted().collect(Collectors.toList());
  }

  /**
   * Sets the excludePatterns.
   *
   * @param excludePatterns The excludePatterns.
   * @return This.
   */
  public FileSet withExcludePatterns(List<Pattern> excludePatterns) {
    this.excludePatterns.clear();
    if (excludePatterns != null) {
      this.excludePatterns.addAll(excludePatterns);
    }
    return this;
  }

  /**
   * Sets the includePatterns.
   *
   * @param includePatterns The includePatterns.
   * @return This.
   */
  public FileSet withIncludePatterns(List<Pattern> includePatterns) {
    this.includePatterns.clear();
    if (includePatterns != null) {
      this.includePatterns.addAll(includePatterns);
    }
    return this;
  }

  private boolean includeFileInfo(FileInfo fileInfo) {
    if (includePatterns.isEmpty() && excludePatterns.isEmpty()) {
      return true;
    }

    boolean keep = includePatterns.isEmpty();
    for (Pattern includePattern : includePatterns) {
      if (includePattern.asPredicate().test(fileInfo.relative.toString())) {
        keep = true;
        break;
      }
    }

    if (keep) {
      for (Pattern excludePattern : excludePatterns) {
        if (excludePattern.asPredicate().test(fileInfo.relative.toString())) {
          keep = false;
          break;
        }
      }
    }

    return keep;
  }
}
