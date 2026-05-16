/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

/**
 *
 * @author Brian Pontarelli
 */
class Description {
  public String extended = ""

  public String synopsis

  Description(String extended, String synopsis) {
    this.extended = extended != null ? extended : ""
    this.synopsis = synopsis
  }

  public String getExtendedFormatted() {
    StringBuilder build = new StringBuilder(extended.length())
    String[] lines = extended.trim().split("\n")

    lines.each { line ->
      line = line.trim()
      build.append(" ${line.length() == 0 ? "." : line}\n")
    }

    build.deleteCharAt(build.length() - 1)
    return build.toString()
  }
}
