/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.net;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lattejava.BaseUnitTest;
import org.lattejava.security.Algorithm;
import org.lattejava.security.Checksum;
import org.lattejava.security.ChecksumException;
import org.testng.annotations.Test;

import com.sun.net.httpserver.HttpServer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * The net tools test.
 */
public class NetToolsTest extends BaseUnitTest {
  @Test
  public void build() throws URISyntaxException {
    URI uri = NetTools.build("http://www.example.com", "org/apache/commons", "common-collections", "3.0", "commons-collections-3.0.jar");
    assertEquals(uri.toString(), "http://www.example.com/org/apache/commons/common-collections/3.0/commons-collections-3.0.jar");

    uri = NetTools.build("http://www.example.com/", "org/apache/commons", "common-collections", "3.0", "commons-collections-3.0.jar");
    assertEquals(uri.toString(), "http://www.example.com/org/apache/commons/common-collections/3.0/commons-collections-3.0.jar");

    uri = NetTools.build("http://www.example.com/", "/org/apache/commons/", "common-collections", "3.0", "commons-collections-3.0.jar");
    assertEquals(uri.toString(), "http://www.example.com/org/apache/commons/common-collections/3.0/commons-collections-3.0.jar");
  }

  @Test
  public void downloadToFile() throws Exception {
    Path path = NetTools.downloadToPath(new URI("http://localhost:7042/src/test/java/org/lattejava/net/TestFile.txt"), null, null, null);
    String result = new String(Files.readAllBytes(path), "UTF-8");
    assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
  }

  @Test
  public void downloadToFileFromFile() throws Exception {
    var fileURI = projectDir.resolve("src/test/java/org/lattejava/net/TestFile.txt")
                            .toUri();
    Path path = NetTools.downloadToPath(fileURI, null, null, null);
    String result = new String(Files.readAllBytes(path), "UTF-8");
    assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
  }

  @Test
  public void downloadToFileWithChecksum() throws Exception {
    Checksum checksum = Checksum.forBytes(Files.readAllBytes(projectDir.resolve("src/test/java/org/lattejava/net/TestFile.txt")), Algorithm.SHA256);
    Path path = NetTools.downloadToPath(new URI("http://localhost:7042/src/test/java/org/lattejava/net/TestFile.txt"), null, null, checksum);
    String result = new String(Files.readAllBytes(path), "UTF-8");
    assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
  }

  @Test
  public void downloadToFileWithChecksumFailure() throws Exception {
    Checksum checksum = new Checksum("0".repeat(64), new byte[32], Algorithm.SHA256);
    try {
      NetTools.downloadToPath(new URI("http://localhost:7042/src/test/java/org/lattejava/net/TestFile.txt"), null, null, checksum);
      fail("Should have failed");
    } catch (ChecksumException e) {
      // Expected
    }
  }

  @Test
  public void downloadToFileWithUsernameAndPassword() throws Exception {
    HttpServer authServer = makeFileServer(7043, "User", "Pass");

    try {
      Path path = NetTools.downloadToPath(new URI("http://localhost:7043/src/test/java/org/lattejava/net/TestFile.txt"), "User", "Pass", null);
      String result = new String(Files.readAllBytes(path), "UTF-8");
      assertEquals(result.trim(), "This file is a test file for copying and writing and such.");
    } finally {
      authServer.stop(0);
    }
  }
}
