/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.command;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.lattejava.*;
import org.lattejava.cli.runtime.*;
import org.lattejava.output.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Tests the LogoutCommand.
 *
 * @author Brian Pontarelli
 */
public class LogoutCommandTest extends BaseUnitTest {
  @Test
  public void removesTokensAndPreservesOtherProperties() throws Exception {
    Path configFile = Files.createTempDirectory("latte-logout-test").resolve("config.properties");
    Files.writeString(configFile, """
        existing.key=existing-value
        latte.auth.accessToken=the-access-token
        latte.auth.refreshToken=the-refresh-token
        """);

    String out = run(configFile);

    assertTrue(out.contains("You have been logged out."), "Output was [" + out + "]");

    Properties loaded = load(configFile);
    assertEquals(loaded.getProperty("existing.key"), "existing-value");
    assertFalse(loaded.containsKey("latte.auth.accessToken"));
    assertFalse(loaded.containsKey("latte.auth.refreshToken"));
  }

  @Test
  public void reportsNotLoggedInWhenFileAbsent() throws Exception {
    Path configFile = Files.createTempDirectory("latte-logout-test").resolve("config.properties");

    String out = run(configFile);

    assertTrue(out.contains("You are not logged in."), "Output was [" + out + "]");
    assertFalse(Files.exists(configFile));
  }

  @Test
  public void reportsNotLoggedInWhenNoTokensPresent() throws Exception {
    Path configFile = Files.createTempDirectory("latte-logout-test").resolve("config.properties");
    Files.writeString(configFile, "existing.key=existing-value\n");

    String out = run(configFile);

    assertTrue(out.contains("You are not logged in."), "Output was [" + out + "]");
    assertEquals(load(configFile).getProperty("existing.key"), "existing-value");
  }

  private Properties load(Path configFile) throws IOException {
    Properties properties = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      properties.load(is);
    }
    return properties;
  }

  private String run(Path configFile) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(baos));
    try {
      Output output = new SystemOutOutput(false);
      new LogoutCommand(configFile).run(new RuntimeConfiguration(), output, null);
    } finally {
      System.setOut(originalOut);
    }
    return baos.toString();
  }
}
