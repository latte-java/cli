/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.lang;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A collection of runtime tools.
 *
 * @author Brian Pontarelli
 */
public class RuntimeTools {
  public static ProcessResult exec(String... command) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);

    Process process = builder.start();
    InputStream is = process.getInputStream();
    byte[] buf = new byte[1024];
    ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
    int length;
    while ((length = is.read(buf)) != -1) {
      baos.write(buf, 0, length);
    }

    return new ProcessResult(process.waitFor(), baos.toString("UTF-8"));
  }

  /**
   * The result from a process, including the exit code and output.
   */
  public static class ProcessResult {
    public final int exitCode;

    public final String output;

    public ProcessResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }
  }
}
