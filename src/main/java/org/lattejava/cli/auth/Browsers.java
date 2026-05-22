/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.awt.*;
import java.io.*;
import java.net.*;

import org.lattejava.output.*;

/**
 * Opens the user's default web browser to a URL, falling back to printing the URL when no browser can be launched (for
 * example over SSH).
 *
 * @author Brian Pontarelli
 */
public final class Browsers {
  private Browsers() {
  }

  public static void open(String url, Output output) {
    output.infoln("Opening your browser to log in. If it does not open automatically, visit:");
    output.infoln("%s", url);

    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI.create(url));
        return;
      }
    } catch (Exception e) {
      // Fall through to the OS-specific command.
    }

    String os = System.getProperty("os.name").toLowerCase();
    try {
      if (os.contains("mac")) {
        new ProcessBuilder("open", url).start();
      } else if (os.contains("nix") || os.contains("nux")) {
        new ProcessBuilder("xdg-open", url).start();
      }
    } catch (IOException e) {
      // The printed URL above is the fallback.
    }
  }
}
