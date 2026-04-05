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
