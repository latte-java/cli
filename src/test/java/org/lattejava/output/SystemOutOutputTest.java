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
