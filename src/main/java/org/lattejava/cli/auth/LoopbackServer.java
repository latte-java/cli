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
 *
 * @author Brian Pontarelli
 */
public class LoopbackServer {
  private final CompletableFuture<String> codeFuture = new CompletableFuture<>();
  private final String expectedState;
  private final int port;
  private HttpServer server;

  public LoopbackServer(int port, String expectedState) {
    this.port = port;
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

  public void start() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
    } catch (IOException e) {
      throw new RuntimeFailureException("Could not start the local login server on port [" + port + "]. It may already be in use. Message was [" + e.getMessage() + "]", e);
    }
    server.createContext("/callback", this::handle);
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
