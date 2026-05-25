/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.lattejava.BaseUnitTest;
import org.lattejava.cli.auth.CredentialStore;
import org.lattejava.cli.auth.Tokens;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.output.SystemOutOutput;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the LatteProcess against a stub publish API and a stub presigned-URL target, using a temporary configuration
 * file for the stored tokens.
 *
 * @author Brian Pontarelli
 */
public class LatteProcessTest extends BaseUnitTest {
  private final SystemOutOutput output = new SystemOutOutput(false);

  @Test
  public void fetchReturnsNull() throws Exception {
    Path configFile = Files.createTempDirectory("latte-process-test").resolve("config.properties");
    LatteProcess process = new LatteProcess(output, "http://localhost:8920", configFile,
        new PublishAPIClient("http://localhost:8920", HttpClient.newHttpClient()));

    ResolvableItem item = new ResolvableItem("org.example", "myproject", "myproject", "1.0.0", "myproject-1.0.0.jar");
    assertNull(process.fetch(item, new PublishWorkflow()));
  }

  @Test
  public void publishFailsWhenNotLoggedIn() throws Exception {
    Path configFile = Files.createTempDirectory("latte-process-test").resolve("config.properties");
    LatteProcess process = new LatteProcess(output, "http://localhost:8921", configFile,
        new PublishAPIClient("http://localhost:8921", HttpClient.newHttpClient()));

    Path artifact = Files.createTempFile("latte-artifact", ".jar");
    Files.writeString(artifact, "artifact-bytes");
    ResolvableItem item = new ResolvableItem("org.example", "myproject", "myproject", "1.0.0", "myproject-1.0.0.jar");

    try {
      process.publish(new FetchResult(artifact, ItemSource.LATTE, item));
      fail("Should have thrown");
    } catch (ProcessFailureException e) {
      assertTrue(e.getMessage().contains("latte login"), e.getMessage());
    }
  }

  @Test
  public void publishUploadsBytesAndPersistsRefreshedTokens() throws Exception {
    Path configFile = Files.createTempDirectory("latte-process-test").resolve("config.properties");
    new CredentialStore(configFile).store(new Tokens("AT", "RT"));

    Path artifact = Files.createTempFile("latte-artifact", ".jar");
    Files.writeString(artifact, "artifact-bytes");

    AtomicReference<String> requestedKey = new AtomicReference<>();
    AtomicReference<byte[]> uploaded = new AtomicReference<>();

    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8922), 0);
    server.createContext("/api/v1/publish/org.example", exchange -> {
      requestedKey.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      exchange.getResponseHeaders().add("X-Access-Token", "new-AT");
      exchange.getResponseHeaders().add("X-Refresh-Token", "new-RT");
      byte[] body = "{\"url\":\"http://localhost:8922/upload\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.getResponseBody().close();
    });
    server.createContext("/upload", exchange -> {
      uploaded.set(exchange.getRequestBody().readAllBytes());
      exchange.sendResponseHeaders(200, -1);
      exchange.getResponseBody().close();
    });
    server.start();

    try {
      LatteProcess process = new LatteProcess(output, "http://localhost:8922", configFile,
          new PublishAPIClient("http://localhost:8922", HttpClient.newHttpClient()));
      ResolvableItem item = new ResolvableItem("org.example", "myproject", "myproject", "1.0.0", "myproject-1.0.0.jar");

      Path result = process.publish(new FetchResult(artifact, ItemSource.LATTE, item));

      assertNull(result);
      assertEquals(new String(uploaded.get(), StandardCharsets.UTF_8), "artifact-bytes");
      assertTrue(requestedKey.get().replace("\\/", "/").contains("org/example/myproject/1.0.0/myproject-1.0.0.jar"), requestedKey.get());

      Tokens persisted = new CredentialStore(configFile).load();
      assertEquals(persisted.accessToken(), "new-AT");
      assertEquals(persisted.refreshToken(), "new-RT");
    } finally {
      server.stop(0);
    }
  }
}
