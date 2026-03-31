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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.lattejava.io.Directory;
import org.lattejava.io.FileInfo;
import org.lattejava.io.FileSet;

/**
 * Helps build Jar files.
 *
 * @author Brian Pontarelli
 */
public class JarBuilder {
  public final List<Directory> directories = new ArrayList<>();

  public final Path file;

  public final List<FileSet> fileSets = new ArrayList<>();

  public Manifest manifest = new Manifest();

  public JarBuilder(String file) {
    this(Paths.get(file));
  }

  public JarBuilder(Path file) {
    this.file = file;
  }

  public int build() throws IOException {
    if (Files.exists(file)) {
      Files.delete(file);
    }

    if (!Files.isDirectory(file.getParent())) {
      Files.createDirectories(file.getParent());
    }

    // Sort the file infos and add the directories
    Set<FileInfo> fileInfos = new TreeSet<>();
    for (FileSet fileSet : fileSets) {
      Set<Directory> dirs = fileSet.toDirectories();
      dirs.removeAll(directories);
      for (Directory dir : dirs) {
        directories.add(dir);
      }

      fileInfos.addAll(fileSet.toFileInfos());
    }

    int count = 0;

    // Preemptively append a slash so the set will remove any duplicate directories, including META-INF/
    for (Directory directory : directories) {
      if (!directory.name.endsWith("/")) {
        //noinspection StringConcatenationInLoop
        directory.name = directory.name + "/";
      }
    }

    // Ensure there is a META-INF directory because our JAR files always have a MANIFEST.MF file
    directories.add(new Directory("META-INF/"));

    try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(file), manifest)) {
      for (Directory directory : new HashSet<>(directories)) {
        jos.putNextEntry(new JarEntry(directory.name));
        jos.closeEntry();
        count++;
      }

      for (FileInfo fileInfo : fileInfos) {
        JarEntry entry = new JarEntry(fileInfo.relative.toString());
        entry.setCreationTime(fileInfo.creationTime);
        entry.setLastAccessTime(fileInfo.lastAccessTime);
        entry.setLastModifiedTime(fileInfo.lastModifiedTime);
        entry.setSize(fileInfo.size);
        jos.putNextEntry(entry);
        Files.copy(fileInfo.origin, jos);
        jos.flush();
        jos.closeEntry();
        count++;
      }
    }

    return count;
  }

  public JarBuilder directory(Directory directory) {
    directories.add(directory);
    return this;
  }

  public JarBuilder ensureManifest(String vendor, String version) {
    manifest.getMainAttributes().putIfAbsent(Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putIfAbsent(Name.IMPLEMENTATION_VENDOR, vendor);
    manifest.getMainAttributes().putIfAbsent(Name.IMPLEMENTATION_VERSION, version);
    manifest.getMainAttributes().putIfAbsent(Name.SPECIFICATION_VENDOR, vendor);
    manifest.getMainAttributes().putIfAbsent(Name.SPECIFICATION_VERSION, version);
    return this;
  }

  public JarBuilder fileSet(Path directory) throws IOException {
    return fileSet(new FileSet(directory));
  }

  public JarBuilder fileSet(String directory) throws IOException {
    return fileSet(Paths.get(directory));
  }

  public JarBuilder fileSet(FileSet fileSet) throws IOException {
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    if (!Files.isDirectory(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] does not exist");
    }

    fileSets.add(fileSet);
    return this;
  }

  public JarBuilder manifest(Path file) throws IOException {
    try (InputStream is = Files.newInputStream(file)) {
      manifest.read(is);
    }
    return this;
  }

  public JarBuilder manifest(Map<String, Object> map) {
    Attributes attributes = manifest.getMainAttributes();
    map.forEach((key, value) -> attributes.put(new Name(key), value.toString()));
    return this;
  }

  public JarBuilder optionalFileSet(Path directory) throws IOException {
    return optionalFileSet(new FileSet(directory));
  }

  public JarBuilder optionalFileSet(String directory) throws IOException {
    return optionalFileSet(Paths.get(directory));
  }

  public JarBuilder optionalFileSet(FileSet fileSet) throws IOException {
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    // Only add if it exists
    if (Files.isDirectory(fileSet.directory)) {
      fileSets.add(fileSet);
    }

    return this;
  }
}