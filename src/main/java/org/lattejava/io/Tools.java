/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.io;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tools for helping convert objects to FileSets and other IO classes.
 *
 * @author Brian Pontarelli
 */
public class Tools {
  /**
   * Converts all of the objects in the list to Patterns.
   *
   * @param list The list of objects.
   * @return The list of patterns.
   */
  @SuppressWarnings("unchecked")
  public static List<Pattern> toPatterns(List list) {
    if (list == null) {
      return null;
    }

    for (int i = 0; i < list.size(); i++) {
      Object item = list.get(i);
      if (!(item instanceof Pattern)) {
        list.set(i, Pattern.compile(item.toString()));
      }
    }

    return list;
  }

  /**
   * Converts the object to a String (or null).
   *
   * @param value The value object.
   * @return The toString or null.
   */
  public static String toString(Object value) {
    if (value == null) {
      return null;
    }

    return value.toString();
  }
}
