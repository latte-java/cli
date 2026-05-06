/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.output;

import java.io.PrintStream;

/**
 * Escape sequences for ANSI 256 colors.
 *
 * @author Brian Pontarelli
 */
public final class Ansi256Colors {
  public static final char ESCAPE = 0x1b;

  public static final int ERROR = 124;

  public static final int WARNING = 214;

  public static void setColor(PrintStream stream, int foreground) {
    stream.print(ESCAPE);
    stream.print("[38;5;");
    stream.print(foreground);
    stream.print("m");
  }

  public static void setColor(StringBuilder build, int foreground) {
    build.append(ESCAPE);
    build.append("[38;5;");
    build.append(foreground);
    build.append("m");
  }

  public static void clear(PrintStream stream) {
    stream.print(ESCAPE);
    stream.print("[0m");
  }

  public static void clear(StringBuilder build) {
    build.append(ESCAPE);
    build.append("[0m");
  }
}
