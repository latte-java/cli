/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.output;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the output class.
 *
 * @author Brian Pontarelli
 */
public class SystemOutOutputTest {
  public static void main(String[] args) {
    Output output = new SystemOutOutput(true);
    output.errorln("Error");
    output.warningln("Warning");
    output.infoln("Info");
  }

  @Test
  public void noColor() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    Output output = new SystemOutOutput(out, false);
    output.errorln("Error");
    output.warningln("Warning");
    output.infoln("Info");

    assertEquals(baos.toString(), "Error\nWarning\nInfo\n");
  }
}
