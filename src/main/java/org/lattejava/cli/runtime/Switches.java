/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class that models command-line switches that might have values or not.
 *
 * @author Brian Pontarelli
 */
public class Switches {
  public Set<String> booleanSwitches = new HashSet<>();

  public Map<String, List<String>> valueSwitches = new HashMap<>();

  /**
   * Adds a switch that has no value (boolean).
   *
   * @param name The name of the switch.
   */
  public void add(String name) {
    booleanSwitches.add(name);
  }

  /**
   * Adds a switch with the given value.
   *
   * @param name  The name of the switch.
   * @param value The value of the switch.
   */
  public void add(String name, String value) {
    List<String> values = valueSwitches.get(name);
    if (values == null) {
      values = new ArrayList<>();
      valueSwitches.put(name, values);
    }

    values.add(value);
  }

  /**
   * Returns whether or not the given switch was given by the user. This checks booleans and value switches.
   *
   * @param name The name of the switch.
   * @return True if the switch is present (either --foo or --foo=bar).
   */
  public boolean has(String name) {
    return booleanSwitches.contains(name) || valueSwitches.containsKey(name);
  }

  /**
   * Returns whether or not the given switch has the given value.
   *
   * @param name  The name of the switch.
   * @param value The value to check for.
   * @return True if the switch has the value.
   */
  public boolean hasValue(String name, String value) {
    List<String> values = valueSwitches.get(name);
    return values != null && values.contains(value);
  }

  /**
   * Returns the values for the given switch name. If the switch doesn't exist, this returns null.
   *
   * @param name The name of the switch.
   * @return The values or null.
   */
  public String[] values(String name) {
    List<String> values = valueSwitches.get(name);
    if (values == null) {
      return null;
    }

    return values.toArray(new String[values.size()]);
  }
}
