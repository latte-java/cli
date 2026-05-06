/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.domain.Publication;

/**
 * Models the publication set in the project file.
 *
 * @author Brian Pontarelli
 */
public class Publications {
  public final Map<String, List<Publication>> publicationGroups = new HashMap<>();

  /**
   * Adds the given Publication to the given group.
   *
   * @param group       The group.
   * @param publication The Publication.
   * @return This Publications object.
   */
  public Publications add(String group, Publication publication) {
    List<Publication> publications = publicationGroups.get(group);
    if (publications == null) {
      publications = new ArrayList<>();
      publicationGroups.put(group, publications);
    }

    publications.add(publication);
    return this;
  }

  /**
   * @return All of the publications flattened.
   */
  public List<Publication> allPublications() {
    List<Publication> result = new ArrayList<>();
    publicationGroups.forEach((group, list) -> result.addAll(list));
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Publications that = (Publications) o;
    return publicationGroups.equals(that.publicationGroups);
  }

  @Override
  public String toString() {
    return "Publications{" +
        "publicationGroups=" + publicationGroups +
        '}';
  }

  /**
   * Null safe getter for a single group. This returns the list of publications or an empty list if the publication
   * group is empty.
   *
   * @param group The group.
   * @return The list of publications (never null).
   */
  public List<Publication> group(String group) {
    List<Publication> publications = publicationGroups.get(group);
    if (publications == null) {
      return Collections.emptyList();
    }

    return publications;
  }

  @Override
  public int hashCode() {
    return publicationGroups.hashCode();
  }

  /**
   * @return The total number of Publications.
   */
  public int size() {
    int size = 0;
    for (List<Publication> publications : publicationGroups.values()) {
      size += publications.size();
    }
    return size;
  }
}
