/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

/**
 * The priority of the Debian package.
 *
 * @author Brian Pontarelli
 */
class Priority {
  public static final Set<String> values = new HashSet<>(["required", "important", "standard", "optional", "extra"])

  public static boolean isValid(String priority) {
    return values.contains(priority)
  }
}