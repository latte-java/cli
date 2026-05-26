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

    // Launch the browser with the native OS command rather than java.awt.Desktop. On macOS, touching AWT turns this CLI
    // into a GUI application: it gets a Dock icon and appears in the Cmd-Tab app switcher. Shelling out avoids
    // initializing AWT entirely, so the process stays invisible (and the CLI starts a little faster).
    if (launch(browserCommand(url))) {
      return;
    }

    // Fall back to AWT for any platform the native command did not cover, or when it was unavailable. Mark the process
    // as a UI element first so this path also stays out of the Dock and app switcher on macOS.
    try {
      System.setProperty("apple.awt.UIElement", "true");
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI.create(url));
      }
    } catch (Exception e) {
      // The printed URL above is the fallback.
    }
  }

  private static String[] browserCommand(String url) {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("mac")) {
      return new String[]{"open", url};
    } else if (os.contains("nix") || os.contains("nux")) {
      return new String[]{"xdg-open", url};
    } else if (os.contains("win")) {
      return new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
    }

    return null;
  }

  private static boolean launch(String[] command) {
    if (command == null) {
      return false;
    }

    try {
      new ProcessBuilder(command).start();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
