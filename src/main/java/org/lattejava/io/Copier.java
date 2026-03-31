/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides file copying utilities using relative or absolute paths. All relative paths are resolved using the
 * baseDirectory that is passed into the constructor.
 *
 * @author Brian Pontarelli
 */
public class Copier {
  public final Path to;

  public List<FileSet> fileSets = new ArrayList<>();

  public List<Filter> filters = new ArrayList<>();

  public Copier(Path to) {
    this.to = to;
  }

  public Copier(String to) {
    this.to = Paths.get(to);
  }

  public int copy() throws IOException {
    int count = 0;
    for (FileSet fileSet : fileSets) {
      // Skip missing source directories
      if (!Files.isDirectory(fileSet.directory)) {
        continue;
      }

      for (FileInfo fileInfo : fileSet.toFileInfos()) {
        Path target = to.resolve(fileInfo.relative);
        Files.createDirectories(target.getParent());

        if (filters.isEmpty()) {
          Files.copy(fileInfo.origin, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
          // Bold assumption here. I'm assuming the files aren't large and that it will be simpler and faster to read them
          // into memory, filter them, then write it out
          String contents = new String(Files.readAllBytes(fileInfo.origin), "UTF-8");
          for (Filter filter : filters) {
            contents = contents.replaceAll(filter.token, filter.value);
          }
          Files.write(target, contents.getBytes("UTF-8"));
        }

        count++;
      }
    }

    return count;
  }

  public Copier fileSet(FileSet fileSet) throws IOException {
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] passed to the Copier cannot be a file");
    }

    if (!Files.isDirectory(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] passed to the Copier must be a valid directory.");
    }

    this.fileSets.add(fileSet);
    return this;
  }

  public Copier fileSet(Path directory) throws IOException {
    return fileSet(new FileSet(directory));
  }

  public Copier fileSet(String directory) throws IOException {
    return fileSet(Paths.get(directory));
  }

  public Copier filter(String token, String value) {
    this.filters.add(new Filter(token, value));
    return this;
  }

  public Copier filter(Filter filter) {
    this.filters.add(filter);
    return this;
  }

  public Copier optionalFileSet(String directory) throws IOException {
    return optionalFileSet(Paths.get(directory));
  }

  public Copier optionalFileSet(FileSet fileSet) throws IOException {
    if (Files.isRegularFile(fileSet.directory)) {
      throw new IOException("The [fileSet.directory] path [" + fileSet.directory + "] passed to the Copier cannot be a file");
    }

    if (!Files.isDirectory(fileSet.directory)) {
      return this;
    }

    this.fileSets.add(fileSet);
    return this;
  }

  public Copier optionalFileSet(Path directory) throws IOException {
    return optionalFileSet(new FileSet(directory));
  }
}
