/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import org.lattejava.*;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * Tests the CredentialStore.
 *
 * @author Brian Pontarelli
 */
public class CredentialStoreTest extends BaseUnitTest {
  @Test
  public void createsFileAndParentDirectoriesWhenMissing() throws Exception {
    Path dir = Files.createTempDirectory("latte-credential-test");
    Path configFile = dir.resolve("nested/config.properties");

    new CredentialStore(configFile).store(new Tokens("AT", "RT"));

    Properties loaded = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      loaded.load(is);
    }
    assertEquals(loaded.getProperty("latte.auth.accessToken"), "AT");
    assertEquals(loaded.getProperty("latte.auth.refreshToken"), "RT");
  }

  @Test
  public void omitsRefreshTokenWhenNull() throws Exception {
    Path dir = Files.createTempDirectory("latte-credential-test");
    Path configFile = dir.resolve("config.properties");

    new CredentialStore(configFile).store(new Tokens("AT", null));

    Properties loaded = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      loaded.load(is);
    }
    assertEquals(loaded.getProperty("latte.auth.accessToken"), "AT");
    assertFalse(loaded.containsKey("latte.auth.refreshToken"));
  }

  @Test
  public void preservesExistingPropertiesAndStoresTokens() throws Exception {
    Path dir = Files.createTempDirectory("latte-credential-test");
    Path configFile = dir.resolve("config.properties");
    Files.writeString(configFile, "existing.key=existing-value\n");

    new CredentialStore(configFile).store(new Tokens("the-access-token", "the-refresh-token"));

    Properties loaded = new Properties();
    try (InputStream is = Files.newInputStream(configFile)) {
      loaded.load(is);
    }

    assertEquals(loaded.getProperty("existing.key"), "existing-value");
    assertEquals(loaded.getProperty("latte.auth.accessToken"), "the-access-token");
    assertEquals(loaded.getProperty("latte.auth.refreshToken"), "the-refresh-token");
  }

  @Test
  public void restrictsPermissionsOnPosix() throws Exception {
    Path dir = Files.createTempDirectory("latte-credential-test");
    Path configFile = dir.resolve("config.properties");

    new CredentialStore(configFile).store(new Tokens("AT", "RT"));

    if (configFile.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      assertEquals(PosixFilePermissions.toString(Files.getPosixFilePermissions(configFile)), "rw-------");
    }
  }
}
