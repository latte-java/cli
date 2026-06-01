/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.auth.Tokens;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the PublishAPIClient against a stub publish API and a stub presigned-URL target.
 *
 * @author Brian Pontarelli
 */
public class PublishAPIClientTest extends BaseUnitTest {
  @Test
  public void requestPresignedURLCapturesRefreshedTokens() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8911), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      exchange.getResponseHeaders().add("X-Access-Token", "new-AT");
      exchange.getResponseHeaders().add("X-Refresh-Token", "new-RT");
      respond(exchange, 200, "{\"url\":\"http://localhost:8911/upload\"}");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8911", HttpClient.newHttpClient());
      PublishAPIClient.PresignResponse response =
          client.requestPresignedURL("org.example", "org/example/1.0.0/x.jar", new Tokens("AT", "RT"));

      assertEquals(response.url(), "http://localhost:8911/upload");
      assertEquals(response.refreshedTokens().accessToken(), "new-AT");
      assertEquals(response.refreshedTokens().refreshToken(), "new-RT");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void requestPresignedURLSendsCorrectRequestAndParsesURL() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> refreshHeader = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8910), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      method.set(exchange.getRequestMethod());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      refreshHeader.set(exchange.getRequestHeaders().getFirst("X-Refresh-Token"));
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      respond(exchange, 200, "{\"url\":\"https://r2.example.com/bucket/org/example/1.0.0/x.jar?sig\"}");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8910", HttpClient.newHttpClient());
      PublishAPIClient.PresignResponse response =
          client.requestPresignedURL("org.example", "org/example/1.0.0/x.jar", new Tokens("the-access-token", "the-refresh-token"));

      assertEquals(method.get(), "POST");
      assertEquals(authorization.get(), "Bearer the-access-token");
      assertEquals(refreshHeader.get(), "the-refresh-token");
      assertTrue(body.get().contains("\"fileName\""), body.get());
      // json-simple escapes forward slashes as \/; un-escape before asserting on the key.
      assertTrue(body.get().replace("\\/", "/").contains("org/example/1.0.0/x.jar"), body.get());
      assertEquals(response.url(), "https://r2.example.com/bucket/org/example/1.0.0/x.jar?sig");
      assertNull(response.refreshedTokens());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void requestPresignedURLThrowsOnForbidden() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8913), 0);
    server.createContext("/api/v1/publish/org.example", exchange ->
        respond(exchange, 403, "{\"error\":\"ForbiddenException\",\"message\":\"denied\"}"));
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8913", HttpClient.newHttpClient());
      client.requestPresignedURL("org.example", "org/example/1.0.0/x.jar", new Tokens("AT", "RT"));
      fail("Should have thrown");
    } catch (ProcessFailureException e) {
      assertTrue(e.getMessage().contains("not authorized"), e.getMessage());
      assertTrue(e.getMessage().contains("org.example"), e.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void requestPresignedURLThrowsOnUnauthorized() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8912), 0);
    server.createContext("/api/v1/publish/org.example", exchange ->
        respond(exchange, 401, "{\"error\":\"UnauthenticatedException\",\"message\":\"no token\"}"));
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8912", HttpClient.newHttpClient());
      client.requestPresignedURL("org.example", "org/example/1.0.0/x.jar", new Tokens("AT", "RT"));
      fail("Should have thrown");
    } catch (ProcessFailureException e) {
      assertTrue(e.getMessage().contains("latte login"), e.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void requestPresignedURLThrowsWithValidationMessageOn400() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8914), 0);
    server.createContext("/api/v1/publish/org.example", exchange ->
        respond(exchange, 400, "{\"fieldErrors\":{\"fileName\":[{\"code\":\"[outsideNamespace]fileName\",\"message\":\"The file name [com/evil/x.jar] is not within the group namespace [org.example].\"}]},\"generalErrors\":[]}"));
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8914", HttpClient.newHttpClient());
      client.requestPresignedURL("org.example", "com/evil/x.jar", new Tokens("AT", "RT"));
      fail("Should have thrown");
    } catch (ProcessFailureException e) {
      assertTrue(e.getMessage().contains("not within the group namespace"), e.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void uploadPutsBytes() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<byte[]> received = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8915), 0);
    server.createContext("/upload", exchange -> {
      method.set(exchange.getRequestMethod());
      received.set(exchange.getRequestBody().readAllBytes());
      respond(exchange, 200, "");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8915", HttpClient.newHttpClient());
      byte[] payload = "artifact-bytes".getBytes(StandardCharsets.UTF_8);
      client.upload("http://localhost:8915/upload", payload);

      assertEquals(method.get(), "PUT");
      assertEquals(new String(received.get(), StandardCharsets.UTF_8), "artifact-bytes");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void uploadThrowsOnNon200() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8916), 0);
    server.createContext("/upload", exchange -> respond(exchange, 500, "boom"));
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8916", HttpClient.newHttpClient());
      client.upload("http://localhost:8916/upload", "x".getBytes(StandardCharsets.UTF_8));
      fail("Should have thrown");
    } catch (ProcessFailureException e) {
      assertTrue(e.getMessage().contains("500"), e.getMessage());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void verifyPublishPermissionCapturesRefreshedTokens() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8932), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      exchange.getResponseHeaders().add("X-Access-Token", "new-AT");
      exchange.getResponseHeaders().add("X-Refresh-Token", "new-RT");
      respond(exchange, 200, "");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8932", HttpClient.newHttpClient());
      PublishAPIClient.PermissionResponse response = client.verifyPublishPermission("org.example", new Tokens("AT", "RT"));

      assertTrue(response.readiness().ready());
      assertEquals(response.refreshedTokens().accessToken(), "new-AT");
      assertEquals(response.refreshedTokens().refreshToken(), "new-RT");
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void verifyPublishPermissionReportsNotReadyOnForbidden() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8931), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> respond(exchange, 403, ""));
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8931", HttpClient.newHttpClient());
      PublishAPIClient.PermissionResponse response = client.verifyPublishPermission("org.example", new Tokens("AT", "RT"));

      assertFalse(response.readiness().ready());
      assertTrue(response.readiness().message().contains("org.example"), response.readiness().message());
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void verifyPublishPermissionSendsHEADWithAuthorization() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> refreshHeader = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8930), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      method.set(exchange.getRequestMethod());
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      refreshHeader.set(exchange.getRequestHeaders().getFirst("X-Refresh-Token"));
      respond(exchange, 200, "");
    });
    server.start();

    try {
      PublishAPIClient client = new PublishAPIClient("http://localhost:8930", HttpClient.newHttpClient());
      PublishAPIClient.PermissionResponse response = client.verifyPublishPermission("org.example", new Tokens("AT", "RT"));

      assertEquals(method.get(), "HEAD");
      assertEquals(authorization.get(), "Bearer AT");
      assertEquals(refreshHeader.get(), "RT");
      assertTrue(response.readiness().ready());
      assertNull(response.readiness().message());
    } finally {
      server.stop(0);
    }
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.getResponseBody().close();
  }
}
