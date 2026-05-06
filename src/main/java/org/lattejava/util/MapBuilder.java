/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds Maps.
 *
 * @author Brian Pontarelli
 */
public class MapBuilder<T, U> {
  public Map<T, U> map = new LinkedHashMap<>();

  public static <T, U> Map<T, U> simpleMap(T key, U value) {
    return new MapBuilder<T, U>().put(key, value).done();
  }

  public MapBuilder<T, U> put(T key, U value) {
    map.put(key, value);
    return this;
  }

  public Map<T, U> done() {
    return map;
  }
}
