/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

/**
 * The section of the Debian package.
 *
 * @author Brian Pontarelli
 */
class Section {
  public static final String[] PREFIXES = ["", "contrib/", "non-free/"];
  public static final String[] BASIC_SECTIONS = [
      "admin", "base", "comm", "devel", "doc", "editors", "electronics", "embedded", "games", "gnome", "graphics",
      "hamradio", "interpreters", "kde", "libs", "libdevel", "mail", "math", "misc", "net", "news", "oldlibs",
      "otherosfs", "perl", "python", "science", "shells", "sound", "tex", "text", "utils", "web", "x11"
  ]

  public static Set<String> sections = new HashSet<>(PREFIXES.length * BASIC_SECTIONS.length)

  static {
    PREFIXES.each {prefix ->
      BASIC_SECTIONS.each {section ->
        sections.add(prefix + section)
      }
    }
  }

  public static boolean isValid(String section) {
    return sections.contains(section)
  }
}
