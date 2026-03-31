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

import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * A directory that might be empty or might contain other directories or files.
 *
 * @author Brian Pontarelli
 */
public class Directory implements Comparable<Directory> {
  public static final Set<String> REQUIRED_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(asList("name")));

  public static final Set<String> VALID_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(asList("name", "groupName", "mode", "userName")));

  public String groupName;

  public FileTime lastModifiedTime;

  public Integer mode;

  public String name;

  public String userName;

  public Directory(String name) {
    this.name = name;
  }

  public Directory(String name, Integer mode, String userName, String groupName, FileTime lastModifiedTime) {
    this.name = name;
    this.groupName = groupName;
    this.mode = mode;
    this.userName = userName;
    this.lastModifiedTime = lastModifiedTime;
  }

  /**
   * Determines if the attributes given can be used to construct a Directory.
   *
   * @param attributes The attributes.
   * @return Null if the attributes are valid, an error message describing why they aren't valid.
   */
  public static String attributesValid(Map<String, Object> attributes) {
    StringBuilder build = new StringBuilder();
    if (!attributes.keySet().containsAll(REQUIRED_ATTRIBUTES)) {
      build.append("Missing required attributes ").append(REQUIRED_ATTRIBUTES).append(" for a Directory\n");
    }

    Set<String> invalidAttributes = attributes.keySet().stream().filter((attr) -> !VALID_ATTRIBUTES.contains(attr)).collect(Collectors.toSet());
    if (invalidAttributes.size() > 0) {
      build.append("Invalid attributes ").append(invalidAttributes).append(" for a Directory\n");
    }

    if (attributes.containsKey("mode") && !(attributes.get("mode") instanceof Integer)) {
      build.append("The [mode] attribute for an ArchiveFileSet must be an Integer");
    }

    if (build.length() > 0) {
      return build.toString();
    }

    return null;
  }

  /**
   * Constructs a Directory from a Map of attributes.
   *
   * @param attributes The attributes.
   * @return The Directory.
   */
  public static Directory fromAttributes(Map<String, Object> attributes) {
    return new Directory(Tools.toString(attributes.get("name")))
        .withGroupName(Tools.toString(attributes.get("groupName")))
        .withLastModifiedTime((FileTime) attributes.get("lastModifiedTime"))
        .withMode((Integer) attributes.get("mode"))
        .withUserName(Tools.toString(attributes.get("userName")));
  }

  @Override
  public int compareTo(Directory o) {
    return name.compareTo(o.name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Directory directory = (Directory) o;
    return name.equals(directory.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  public String toString() {
    return name;
  }

  /**
   * Sets the groupName.
   *
   * @param groupName The groupName
   * @return This.
   */
  public Directory withGroupName(String groupName) {
    this.groupName = groupName;
    return this;
  }

  /**
   * Sets the lastModifiedTime.
   *
   * @param lastModifiedTime The lastModifiedTime
   * @return This.
   */
  public Directory withLastModifiedTime(FileTime lastModifiedTime) {
    this.lastModifiedTime = lastModifiedTime;
    return this;
  }

  /**
   * Sets the mode.
   *
   * @param mode The mode
   * @return This.
   */
  public Directory withMode(Integer mode) {
    this.mode = mode;
    return this;
  }

  /**
   * Sets the name.
   *
   * @param name The name.
   * @return This.
   */
  public Directory withName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the userName.
   *
   * @param userName The userName
   * @return This.
   */
  public Directory withUserName(String userName) {
    this.userName = userName;
    return this;
  }
}
