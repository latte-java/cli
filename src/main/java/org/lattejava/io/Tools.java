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
