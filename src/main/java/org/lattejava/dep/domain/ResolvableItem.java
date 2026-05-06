/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import java.util.Collections;
import java.util.List;

/**
 * A resolvable item for an artifact. This might be the metadata, JAR, etc.
 *
 * @author Brian Pontarelli
 */
public class ResolvableItem {
  public final List<String> alternativeItems;

  public final String group;

  public final String item;

  public final String name;

  public final String project;

  public final String version;

  public ResolvableItem(String group, String project, String name, String version, String item) {
    this.group = group;
    this.project = project;
    this.name = name;
    this.version = version;
    this.item = item;
    this.alternativeItems = Collections.emptyList();
  }

  public ResolvableItem(String group, String project, String name, String version, String item, List<String> alternativeItems) {
    this.group = group;
    this.project = project;
    this.name = name;
    this.version = version;
    this.item = item;
    this.alternativeItems = alternativeItems != null && !alternativeItems.isEmpty() ? List.copyOf(alternativeItems) : Collections.emptyList();
  }

  /**
   * Copy constructor — drops alternatives (used for checksum, neg markers, etc.)
   */
  public ResolvableItem(ResolvableItem other, String item) {
    this.group = other.group;
    this.item = item;
    this.name = other.name;
    this.project = other.project;
    this.version = other.version;
    this.alternativeItems = Collections.emptyList();
  }

  @Override
  public String toString() {
    return group + ":" + project + ":" + name + ":" + version + ":" + item;
  }
}
