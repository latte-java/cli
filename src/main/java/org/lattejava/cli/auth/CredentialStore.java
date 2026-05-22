/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

import org.lattejava.cli.runtime.*;

/**
 * Writes the OAuth tokens into the Latte global configuration file, merging with any properties already present and
 * restricting the file permissions to the owner where the filesystem supports it.
 *
 * @author Brian Pontarelli
 */
public class CredentialStore {
  static final String ACCESS_TOKEN_KEY = "latte.auth.accessToken";
  static final String REFRESH_TOKEN_KEY = "latte.auth.refreshToken";

  private final Path configFile;

  public CredentialStore(Path configFile) {
    this.configFile = configFile;
  }

  public void store(Tokens tokens) {
    Properties properties = new Properties();
    if (Files.isRegularFile(configFile)) {
      try (InputStream is = Files.newInputStream(configFile)) {
        properties.load(is);
      } catch (IOException e) {
        throw new RuntimeFailureException("Unable to read the configuration file [" + configFile + "]. Message was [" + e.getMessage() + "]", e);
      }
    }

    properties.setProperty(ACCESS_TOKEN_KEY, tokens.accessToken());
    if (tokens.refreshToken() != null) {
      properties.setProperty(REFRESH_TOKEN_KEY, tokens.refreshToken());
    }

    Path parent = configFile.getParent();
    Path directory = parent != null ? parent : configFile.toAbsolutePath().getParent();
    Path temp = null;
    try {
      Files.createDirectories(directory);

      // Write to a sibling temp file and atomically move it into place so a failed write never truncates the existing
      // configuration (which may hold unrelated credentials).
      temp = Files.createTempFile(directory, "config", ".properties");
      try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        properties.store(writer, "Latte configuration");
      }
      restrictPermissions(temp);

      try {
        Files.move(temp, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
      }
      temp = null;
    } catch (IOException e) {
      throw new RuntimeFailureException("Unable to write the configuration file [" + configFile + "]. Message was [" + e.getMessage() + "]", e);
    } finally {
      if (temp != null) {
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
          // The temp file is best-effort cleanup; nothing actionable if it cannot be removed.
        }
      }
    }
  }

  private void restrictPermissions(Path file) throws IOException {
    if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
  }
}
