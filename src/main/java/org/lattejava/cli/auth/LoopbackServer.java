/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.auth;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import org.lattejava.cli.runtime.*;
import org.lattejava.http.server.*;

/**
 * A single-use local HTTP server that listens on the loopback interface for the OAuth redirect, validates the
 * {@code state} parameter, and exposes the captured authorization code.
 * <p>
 * The server binds an ephemeral port chosen by the operating system rather than a fixed one, so a login never collides
 * with another process or with a second concurrent {@code latte login}. Because the port is not known until
 * {@link #start()} has bound it, the redirect URI is derived from the server rather than being a constant, and the IdP
 * must authorize the whole {@code http://127.0.0.1:*&#47;callback} pattern instead of a single URL.
 * <p>
 * The host is the IPv4 loopback literal rather than {@code localhost}, per RFC 8252 section 8.3, which says using
 * {@code localhost} is NOT RECOMMENDED. The literal guarantees the server can never inadvertently bind a non-loopback
 * interface, and it skips name resolution altogether — on a dual-stack host {@code localhost} can resolve to {@code ::1}
 * for one process and {@code 127.0.0.1} for another, so a server bound by name and a browser resolving the same name can
 * end up on different addresses.
 *
 * @author Brian Pontarelli
 */
public class LoopbackServer {
  public static final String CALLBACK_PATH = "/callback";
  public static final String LOOPBACK_HOST = "127.0.0.1";

  // The Latte HTTP server narrates its lifecycle at INFO through System.Logger, which the default JUL configuration
  // prints to the console. A CLI login must not spray "Starting the HTTP server. Buckle up!" across the user's terminal,
  // so the package logger is pinned to WARNING. The reference is held statically because the LogManager only weakly
  // retains loggers, and a collected logger would silently revert to the inherited level.
  private static final Logger httpLogger = Logger.getLogger("org.lattejava.http");

  static {
    httpLogger.setLevel(Level.WARNING);
  }

  private final CompletableFuture<String> codeFuture = new CompletableFuture<>();
  private final String expectedState;
  private HTTPServer server;

  public LoopbackServer(String expectedState) {
    this.expectedState = expectedState;
  }

  public String awaitCode(Duration timeout) {
    try {
      return codeFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeFailureException("Timed out after [" + timeout.toSeconds() + "] seconds waiting for the login to complete in the browser.");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeFailureException failure) {
        throw failure;
      }
      throw new RuntimeFailureException("The login failed. Message was [" + e.getCause().getMessage() + "]", e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeFailureException("The login was interrupted.", e);
    }
  }

  /**
   * Returns the ephemeral port the operating system assigned when the server was bound. Only valid once {@link #start()}
   * has been called.
   *
   * @return The bound port.
   */
  public int port() {
    if (server == null) {
      throw new IllegalStateException("The loopback server has not been started, so it has no port yet.");
    }

    return server.getActualPort();
  }

  /**
   * Returns the OAuth redirect URI that points at this server. Only valid once {@link #start()} has been called, since
   * the port is assigned at bind time.
   *
   * @return The redirect URI.
   */
  public String redirectURI() {
    return "http://" + LOOPBACK_HOST + ":" + port() + CALLBACK_PATH;
  }

  public void start() {
    try {
      // Port 0 asks the OS for any free ephemeral port, which getActualPort() reports back once the listener is bound.
      // Passing the IP literal to getByName keeps this an exact-address bind with no DNS lookup, so the bound address
      // always matches the host advertised in redirectURI().
      server = new HTTPServer().withHandler(this::handle)
                               .withListener(new HTTPListenerConfiguration(InetAddress.getByName(LOOPBACK_HOST), 0))
                               .start();
    } catch (UnknownHostException | IllegalStateException e) {
      throw new RuntimeFailureException("Could not start the local login server on the loopback interface. Message was [" + e.getMessage() + "]", e);
    }
  }

  public void stop() {
    if (server != null) {
      server.close();
    }
  }

  private void handle(HTTPRequest request, HTTPResponse response) throws IOException {
    // Unlike the JDK server, which only routes registered contexts, the Latte server sends every path to this handler.
    // Anything that is not the callback is a 404 and must not resolve the future.
    if (!CALLBACK_PATH.equals(request.getPath())) {
      response.setStatus(404);
      response.setContentLength(0);
      return;
    }

    // getURLParameter reads the query string only, and the server has already URL-decoded the values.
    String error = request.getURLParameter("error");
    String state = request.getURLParameter("state");

    String code = null;
    RuntimeFailureException failure = null;
    if (error != null) {
      failure = new RuntimeFailureException("Authorization failed with error [" + error + "]");
    } else if (!Objects.equals(expectedState, state)) {
      failure = new RuntimeFailureException("The login response state did not match. This may indicate a CSRF attempt or a stale login.");
    } else if (request.getURLParameter("code") == null) {
      failure = new RuntimeFailureException("The login response did not contain an authorization code.");
    } else {
      code = request.getURLParameter("code");
    }

    // Send and flush the full response to the browser BEFORE completing the future. Completing the future unblocks the
    // main thread in awaitCode, which immediately stops the server in its finally block; if that happened first the
    // server would tear down while this response was still in flight and the browser would render a broken page.
    byte[] body = loadPage(failure == null).getBytes(StandardCharsets.UTF_8);
    response.setStatus(200);
    response.setContentType("text/html; charset=utf-8");
    response.setContentLength(body.length);
    try (OutputStream out = response.getOutputStream()) {
      out.write(body);
    }

    if (failure != null) {
      codeFuture.completeExceptionally(failure);
    } else {
      codeFuture.complete(code);
    }
  }

  /**
   * Loads the fully styled HTML page shown in the browser once the OAuth redirect lands. The pages are coffee-shop
   * themed confirmations built to match the Latte Java brand: the slate palette and blue accent from lattejava.org, the
   * inline Latte logo, and a bit of barista humor. The success page prints a mock receipt; the error page dims the logo
   * and voids the order. Each page is entirely self-contained — the logo is inlined and the type uses the system font
   * stack — so it renders with no external network requests. The pages live as resources in the JAR.
   *
   * @param success Whether the login succeeded.
   * @return The complete HTML document.
   */
  private String loadPage(boolean success) {
    String resource = success ? "/auth/success.html" : "/auth/error.html";
    try (InputStream is = LoopbackServer.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new RuntimeFailureException("Could not find the login result page resource [" + resource + "].");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Could not load the login result page resource [" + resource + "]. Message was [" + e.getMessage() + "]", e);
    }
  }
}
