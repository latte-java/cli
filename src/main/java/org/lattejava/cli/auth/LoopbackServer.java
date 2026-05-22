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

    String message;
    if (params.containsKey("error")) {
      codeFuture.completeExceptionally(new RuntimeFailureException("Authorization failed with error [" + params.get("error") + "]"));
      message = "Login failed. You can close this tab and return to the terminal.";
    } else if (!Objects.equals(expectedState, params.get("state"))) {
      codeFuture.completeExceptionally(new RuntimeFailureException("The login response state did not match. This may indicate a CSRF attempt or a stale login."));
      message = "Login failed. You can close this tab and return to the terminal.";
    } else if (params.get("code") == null) {
      codeFuture.completeExceptionally(new RuntimeFailureException("The login response did not contain an authorization code."));
      message = "Login failed. You can close this tab and return to the terminal.";
    } else {
      codeFuture.complete(params.get("code"));
      message = "Login complete. You can close this tab and return to the terminal.";
    }

    byte[] body = ("<!DOCTYPE html><html><body><h2>" + message + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(200, body.length);
    exchange.getResponseBody().write(body);
    exchange.getResponseBody().close();
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
}
