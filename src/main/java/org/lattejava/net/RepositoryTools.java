/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.net;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

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
                                       .uri(URI.create("https://api.lattejava.org/repository/search?id=" + encodedId + "&latest=true"))
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
