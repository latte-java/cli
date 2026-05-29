/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.command;

import java.nio.file.Path;

import org.lattejava.cli.auth.CredentialStore;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.output.Output;
import org.lattejava.util.LattePaths;

/**
 * Logs the user out of the Latte IdP by deleting the stored access and refresh tokens from the global configuration
 * file. Any unrelated configuration properties are preserved.
 * <p>
 * Usage:
 * <pre>
 *   latte logout
 * </pre>
 *
 * @author Brian Pontarelli
 */
public class LogoutCommand implements Command {
  private final Path configFile;

  public LogoutCommand() {
    this(LattePaths.get().configDir().resolve("config.properties"));
  }

  LogoutCommand(Path configFile) {
    this.configFile = configFile;
  }

  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    boolean removed = new CredentialStore(configFile).clear();
    if (removed) {
      output.infoln("You have been logged out.");
    } else {
      output.infoln("You are not logged in.");
    }
  }
}
