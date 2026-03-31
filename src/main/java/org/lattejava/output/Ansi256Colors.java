/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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
