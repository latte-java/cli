/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import org.lattejava.output.Output;

/**
 * Opens a URL for the user to complete the interactive part of the OAuth login. The production implementation launches
 * the system web browser ({@link Browsers#open}); tests substitute an implementation that drives the login over HTTP.
 *
 * @author Brian Pontarelli
 */
@FunctionalInterface
public interface Browser {
  void open(String url, Output output);
}
