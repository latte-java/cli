/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.net;

import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.time.*;

import org.json.simple.*;
import org.json.simple.parser.*;

/**
 * Utilities for querying the Latte repository search API.
 *
 * @author Brian Pontarelli
 */
public class RepositoryTools {
  private static final HttpClient httpClient = HttpClient.newBuilder()
                                                         .connectTimeout(Duration.ofSeconds(10))
                                                         .followRedirects(HttpClient.Redirect.NORMAL)
                                                         .build();

  /**
   * Queries the Latte repository search API for the latest version of an artifact.
   *
   * @param artifactId The artifact ID in Latte format (e.g., "org.lattejava.plugin:dependency").
   * @return The latest version string, or null if the artifact was not found or the API is unreachable.
   */
  public static String queryLatestVersion(String artifactId) {
    try {
      String encodedId = URLEncoder.encode(artifactId, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder()
                                       .uri(URI.create("https://api.lattejava.org/api/v1/repository/search?id=" + encodedId + "&latest=true"))
                                       .GET()
                                       .timeout(Duration.ofMillis(10_000))
                                       .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return null;
      }
      JSONObject json = (JSONObject) new JSONParser().parse(response.body());
      JSONArray versions = (JSONArray) json.get("versions");
      if (versions == null || versions.isEmpty()) {
        return null;
      }
      return (String) versions.getFirst();
    } catch (Exception e) {
      return null;
    }
  }
}
