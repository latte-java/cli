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
package org.lattejava.io.tar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.lattejava.io.Directory;
import org.lattejava.io.FileInfo;
import org.lattejava.io.FileSet;
import org.lattejava.io.FileTools;

/**
 * Helps build Tar files.
 *
 * @author Brian Pontarelli
 */
public class TarBuilder {
  public final Set<Directory> directories = new TreeSet<>();

  public final Path file;

  public final List<FileSet> fileSets = new ArrayList<>();

  public boolean compress = true;

  public boolean storeGroupName = false;

  public boolean storeUserName = false;

  public TarBuilder(String file) {
    this(Paths.get(file));
  }

  public TarBuilder(Path file) {
    this.file = file;

    // Guess at the compression
    this.compress = file.toString().endsWith(".gz");
  }

  /**
   * Builds the TAR file using the fileSets and Directories provided.
   *
   * @return The number of entries added to the TAR file including the directories.
   * @throws IOException If the build fails.
   */
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
    OutputStream os = Files.newOutputStream(file);
    try (TarArchiveOutputStream tos = new TarArchiveOutputStream(compress ? new GZIPOutputStream(os) : os)) {
      tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

      for (Directory directory : directories) {
        String name = directory.name;
        TarArchiveEntry entry = new TarArchiveEntry(name.endsWith("/") ? name : name + "/");
        if (directory.lastModifiedTime != null) {
          entry.setModTime(directory.lastModifiedTime.toMillis());
        }
        if (directory.mode != null) {
          entry.setMode(FileTools.toMode(directory.mode));
        }
        if (storeGroupName && directory.groupName != null) {
          entry.setGroupName(directory.groupName);
        }
        if (storeUserName && directory.userName != null) {
          entry.setUserName(directory.userName);
        }
        tos.putArchiveEntry(entry);
        tos.closeArchiveEntry();
        count++;
      }

      for (FileInfo fileInfo : fileInfos) {
        TarArchiveEntry entry = new TarArchiveEntry(fileInfo.relative.toString());
        entry.setModTime(fileInfo.lastModifiedTime.toMillis());
        if (storeGroupName) {
          entry.setGroupName(fileInfo.groupName);
        }
        if (storeUserName) {
          entry.setUserName(fileInfo.userName);
        }
        entry.setSize(fileInfo.size);
        entry.setMode(fileInfo.toMode());
        tos.putArchiveEntry(entry);
        Files.copy(fileInfo.origin, tos);
        tos.closeArchiveEntry();
        count++;
      }
    }

    return count;
  }

  public TarBuilder directory(Directory directory) throws IOException {
    directories.add(directory);
    return this;
  }

  public long getExplodedSize() throws IOException {
    long size = 0;
    for (FileSet fileSet : fileSets) {
      for (FileInfo info : fileSet.toFileInfos()) {
        size += info.size;
      }
    }

    return size;
  }

  public TarBuilder fileSet(Path directory) throws IOException {
    return fileSet(new FileSet(directory));
  }

  public TarBuilder fileSet(String directory) throws IOException {
    return fileSet(Paths.get(directory));
  }

  public TarBuilder fileSet(FileSet fileSet) throws IOException {
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] is a file and must be a directory");
    }

    if (!Files.isDirectory(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] does not exist");
    }

    fileSets.add(fileSet);
    return this;
  }

  public TarBuilder optionalFileSet(Path directory) throws IOException {
    return optionalFileSet(new FileSet(directory));
  }

  public TarBuilder optionalFileSet(String directory) throws IOException {
    return optionalFileSet(Paths.get(directory));
  }

  public TarBuilder optionalFileSet(FileSet fileSet) throws IOException {
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
