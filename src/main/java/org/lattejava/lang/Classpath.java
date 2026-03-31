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
package org.lattejava.lang;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Models a Classpath.
 *
 * @author Brian Pontarelli
 */
public class Classpath {
  public final List<Path> paths = new ArrayList<>();

  /**
   * Constructs a Classpath with the given initial parts.
   *
   * @param paths The paths to add to the Classpath on construction.
   */
  public Classpath(String... paths) {
    for (String path : paths) {
      this.paths.add(Paths.get(path));
    }
  }

  /**
   * Adds the given path to the Classpath.
   *
   * @param path The path to add.
   * @return This Classpath.
   */
  public Classpath path(String path) {
    paths.add(Paths.get(path));
    return this;
  }

  /**
   * Adds the given path to the Classpath.
   *
   * @param path The path to add.
   * @return This Classpath.
   */
  public Classpath path(Path path) {
    paths.add(path);
    return this;
  }

  /**
   * Adds the given path to the Classpath.
   *
   * @param file The file to add.
   * @return This Classpath.
   */
  public Classpath path(File file) {
    paths.add(file.toPath());
    return this;
  }

  /**
   * Adds all the given paths to the Classpath.
   *
   * @param paths The paths to add to the Classpath.
   * @return This Classpath.
   */
  public Classpath paths(Path... paths) {
    Collections.addAll(this.paths, paths);
    return this;
  }

  /**
   * Converts this Classpath to a String by joining the paths using the File.separator. If the Classpath is empty, this
   * returns an empty String.
   *
   * @return The Classpath as a String or an empty String.
   */
  public String toString() {
    if (paths.isEmpty()) {
      return "";
    }

    return String.join(File.pathSeparator, paths.stream().map(Path::toString).collect(Collectors.toList()));
  }

  /**
   * Converts this Classpath to a String by joining the paths using the File.separator and adding the prefix to the
   * start. If the Classpath is empty, this returns an empty String.
   *
   * @param prefix The prefix of the String (usually '-classpath ').
   * @return The Classpath as a String or an empty String.
   */
  public String toString(String prefix) {
    if (paths.isEmpty()) {
      return "";
    }

    return prefix + toString();
  }

  public URLClassLoader toURLClassLoader() throws IllegalStateException {
    List<URL> urls = new ArrayList<>();
    for (Path path : paths) {
      try {
        urls.add(path.toUri().toURL());
      } catch (MalformedURLException e) {
        // Very unexpected, rethrow as a plain IllegalStateException
        throw new IllegalStateException(e);
      }
    }

    return new URLClassLoader(urls.toArray(new URL[urls.size()]));
  }
}
