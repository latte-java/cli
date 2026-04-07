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
package org.lattejava.cli.runtime;

import java.util.HashSet;
import java.util.List;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests the default runtime configuration parser.
 *
 * @author Brian Pontarelli
 */
public class DefaultRuntimeConfigurationParserTest extends BaseUnitTest {
  @Test
  public void parse() throws Exception {
    DefaultRuntimeConfigurationParser parser = new DefaultRuntimeConfigurationParser();
    RuntimeConfiguration config = parser.parse("foo", "bar");
    assertTrue(config.colorizeOutput);
    assertEquals(config.args, List.of("foo", "bar"));

    config = parser.parse("foo", "bar", "--noColor");
    assertFalse(config.colorizeOutput);
    assertEquals(config.args, List.of("foo", "bar"));

    config = parser.parse("--noColor", "foo", "bar");
    assertFalse(config.colorizeOutput);
    assertEquals(config.args, List.of("foo", "bar"));

    config = parser.parse("foo", "--noColor", "bar");
    assertFalse(config.colorizeOutput);
    assertEquals(config.args, List.of("foo", "bar"));

    config = parser.parse("foo", "--noColor", "bar", "--test=SomeTest", "--booleanSwitch");
    assertFalse(config.colorizeOutput);
    assertEquals(config.args, List.of("foo", "bar"));
    assertEquals(config.switches.booleanSwitches, new HashSet<>(List.of("booleanSwitch")));
    assertEquals(config.switches.valueSwitches.get("test"), List.of("SomeTest"));
  }

  @Test
  public void parseCommand() {
    DefaultRuntimeConfigurationParser parser = new DefaultRuntimeConfigurationParser();

    // "init" as first non-switch argument is a command
    RuntimeConfiguration config = parser.parse("init");
    assertEquals(config.command, "init");
    assertEquals(config.args, List.of());

    // Switches before command
    config = parser.parse("--debug", "init");
    assertEquals(config.command, "init");
    assertTrue(config.debug);
    assertEquals(config.args, List.of());

    // Non-command first argument is a target, not a command
    config = parser.parse("clean", "init");
    assertNull(config.command);
    assertEquals(config.args, List.of("clean", "init"));

    // Unknown word is a target
    config = parser.parse("build");
    assertNull(config.command);
    assertEquals(config.args, List.of("build"));
  }
}
