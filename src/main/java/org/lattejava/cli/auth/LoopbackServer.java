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

import com.sun.net.httpserver.*;
import org.lattejava.cli.runtime.*;

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

  private final CompletableFuture<String> codeFuture = new CompletableFuture<>();
  private final String expectedState;
  private HttpServer server;

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

    return server.getAddress().getPort();
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
      // Port 0 asks the OS for any free ephemeral port. HttpServer.create binds immediately, so the assigned port is
      // readable from getAddress() as soon as this returns. Passing the IP literal keeps this an exact-address bind with
      // no name lookup, so the bound address always matches the host advertised in redirectURI().
      server = HttpServer.create(new InetSocketAddress(LOOPBACK_HOST, 0), 0);
    } catch (IOException e) {
      throw new RuntimeFailureException("Could not start the local login server on the loopback interface. Message was [" + e.getMessage() + "]", e);
    }
    server.createContext(CALLBACK_PATH, this::handle);
    server.start();
  }

  public void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());

    String code = null;
    RuntimeFailureException failure = null;
    if (params.containsKey("error")) {
      failure = new RuntimeFailureException("Authorization failed with error [" + params.get("error") + "]");
    } else if (!Objects.equals(expectedState, params.get("state"))) {
      failure = new RuntimeFailureException("The login response state did not match. This may indicate a CSRF attempt or a stale login.");
    } else if (params.get("code") == null) {
      failure = new RuntimeFailureException("The login response did not contain an authorization code.");
    } else {
      code = params.get("code");
    }

    // Send and flush the full response to the browser BEFORE completing the future. Completing the future unblocks the
    // main thread in awaitCode, which immediately stops the server in its finally block; if that happened first the
    // server would tear down while this response was still in flight and the browser would render a broken page.
    byte[] body = loadPage(failure == null).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }

    if (failure != null) {
      codeFuture.completeExceptionally(failure);
    } else {
      codeFuture.complete(code);
    }
  }

  private Map<String, String> parseQuery(String query) {
    Map<String, String> result = new HashMap<>();
    if (query == null || query.isEmpty()) {
      return result;
    }

    for (String pair : query.split("&")) {
      int equals = pair.indexOf('=');
      if (equals > 0) {
        result.put(pair.substring(0, equals), pair.substring(equals + 1));
      }
    }

    return result;
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
