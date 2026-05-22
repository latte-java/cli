/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.command;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.lattejava.cli.auth.AuthConfiguration;
import org.lattejava.cli.auth.Browser;
import org.lattejava.cli.auth.Browsers;
import org.lattejava.cli.auth.CredentialStore;
import org.lattejava.cli.auth.JWTs;
import org.lattejava.cli.auth.LoopbackServer;
import org.lattejava.cli.auth.OAuthClient;
import org.lattejava.cli.auth.PKCE;
import org.lattejava.cli.auth.Tokens;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.output.Output;
import org.lattejava.util.LattePaths;

/**
 * Logs the user into the Latte IdP using the OAuth 2.0 Authorization Code flow with PKCE, then stores the resulting
 * access and refresh tokens in the global configuration file.
 * <p>
 * Usage:
 * <pre>
 *   latte login                          # production IdP
 *   latte login http://localhost:9011    # local FusionAuth Docker setup
 * </pre>
 *
 * @author Brian Pontarelli
 */
public class LoginCommand implements Command {
  private final Browser browser;
  private final Path configFile;

  public LoginCommand() {
    this(Browsers::open, LattePaths.get().configDir().resolve("config.properties"));
  }

  LoginCommand(Browser browser, Path configFile) {
    this.browser = browser;
    this.configFile = configFile;
  }

  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    String issuer = configuration.args.isEmpty() ? null : configuration.args.getFirst();
    AuthConfiguration authConfiguration = new AuthConfiguration(issuer);
    PKCE pkce = PKCE.generate();
    String state = randomState();

    LoopbackServer server = new LoopbackServer(AuthConfiguration.CALLBACK_PORT, state);
    server.start();

    String code;
    try {
      browser.open(authConfiguration.authorizeURL(state, pkce.challenge()), output);
      code = server.awaitCode(Duration.ofMinutes(2));
    } finally {
      server.stop();
    }

    Tokens tokens = new OAuthClient(authConfiguration).exchangeCode(code, pkce.verifier());
    new CredentialStore(configFile).store(tokens);

    String email = JWTs.claim(tokens.accessToken(), "email");
    if (email != null) {
      output.infoln("Logged in as [%s]", email);
    } else {
      output.infoln("Login successful.");
    }
  }

  private static String randomState() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
