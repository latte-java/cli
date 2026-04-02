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
 * Basic Latte output system. This writes to Appendables.
 *
 * @author Brian Pontarelli
 */
public class SystemOutOutput implements Output {
  private final boolean colorize;

  private final PrintStream out;

  private boolean debugEnabled;

  public SystemOutOutput(boolean colorize) {
    this.out = System.out;
    this.colorize = colorize;
  }

  SystemOutOutput(PrintStream out, boolean colorize) {
    this.out = out;
    this.colorize = colorize;
  }

  @Override
  public Output debug(String message, Object... values) {
    if (debugEnabled) {
      print(message, values);
    }
    return this;
  }

  @Override
  public Output debugln(String message, Object... values) {
    if (debugEnabled) {
      println(message, values);
    }
    return this;
  }

  @Override
  public Output debug(Throwable t) {
    if (debugEnabled) {
      t.printStackTrace(out);
    }
    return this;
  }

  @Override
  public Output disableDebug() {
    this.debugEnabled = false;
    return this;
  }

  @Override
  public Output enableDebug() {
    this.debugEnabled = true;
    return this;
  }

  @Override
  public Output error(String message, Object... values) {
    if (colorize) {
      Ansi256Colors.setColor(out, Ansi256Colors.ERROR);
    }

    print(message, values);

    if (colorize) {
      Ansi256Colors.clear(out);
    }

    return this;
  }

  @Override
  public Output errorln(String message, Object... values) {
    if (colorize) {
      Ansi256Colors.setColor(out, Ansi256Colors.ERROR);
    }

    println(message, values);

    if (colorize) {
      Ansi256Colors.clear(out);
    }

    return this;
  }

  @Override
  public Output info(String message, Object... values) {
    print(message, values);
    return this;
  }

  @Override
  public Output infoln(String message, Object... values) {
    println(message, values);
    return this;
  }

  @Override
  public Output infoln(int color, String message, Object... values) {
    if (colorize) {
      Ansi256Colors.setColor(out, color);
    }

    println(message, values);

    if (colorize) {
      Ansi256Colors.clear(out);
    }

    return this;
  }

  @Override
  public Output warning(String message, Object... values) {
    if (colorize) {
      Ansi256Colors.setColor(out, Ansi256Colors.WARNING);
    }

    print(message, values);

    if (colorize) {
      Ansi256Colors.clear(out);
    }

    return this;
  }

  @Override
  public Output warningln(String message, Object... values) {
    if (colorize) {
      Ansi256Colors.setColor(out, Ansi256Colors.WARNING);
    }

    println(message, values);

    if (colorize) {
      Ansi256Colors.clear(out);
    }

    return this;
  }

  private void print(String message, Object[] values) {
    if (values.length == 0) {
      out.print(message);
    } else {
      out.printf(message, values);
    }
  }

  private void println(String message, Object[] values) {
    if (values.length == 0) {
      out.println(message);
    } else {
      out.printf(message + "\n", values);
    }
  }
}
