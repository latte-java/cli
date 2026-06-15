/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

import org.json.simple.*;
import org.json.simple.parser.*;
import org.lattejava.cli.auth.*;

/**
 * Talks to the Latte repository publish API: it exchanges the caller's OAuth tokens for a short-lived presigned PUT URL
 * and uploads the artifact bytes to that URL. The API authenticates the caller with the access token and, when needed,
 * refreshes it server-side using the refresh token; any rotated tokens come back in the {@code X-Access-Token} and
 * {@code X-Refresh-Token} response headers and are surfaced on the {@link PresignResponse} so the caller can persist
 * them. It also verifies publish permission for a group with an authenticated {@code HEAD} request via
 * {@link #verifyPublishPermission}, returning the verdict and any rotated tokens on a {@link PermissionResponse}.
 *
 * @author Brian Pontarelli
 */
public class PublishAPIClient {
  private final String apiURL;
  private final HttpClient httpClient;

  /**
   * Creates a new PublishAPIClient.
   *
   * @param apiURL     The base URL of the publish API (e.g. {@code https://api.lattejava.org}).
   * @param httpClient The HTTP client used for the API and upload requests.
   */
  public PublishAPIClient(String apiURL, HttpClient httpClient) {
    this.apiURL = apiURL.endsWith("/") ? apiURL.substring(0, apiURL.length() - 1) : apiURL;
    this.httpClient = httpClient;
  }

  /**
   * Requests a presigned PUT URL for the given object key from the publish API.
   *
   * @param group  The artifact group (the namespace carried in the URL path).
   * @param key    The complete object key to publish to.
   * @param tokens The caller's current access and refresh tokens.
   * @return The presigned URL and any refreshed tokens.
   * @throws ProcessFailureException If the API returns anything other than a 200.
   */
  public PresignResponse requestPresignedURL(String group, String key, Tokens tokens) {
    JSONObject requestBody = new JSONObject();
    requestBody.put("fileName", key);

    HttpRequest.Builder builder = HttpRequest.newBuilder()
                                             .uri(URI.create(apiURL + "/api/v1/publish/" + group))
                                             .header("Authorization", "Bearer " + tokens.accessToken())
                                             .header("Content-Type", "application/json")
                                             .header("Accept", "application/json")
                                             .timeout(Duration.ofSeconds(30))
                                             .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()));
    if (tokens.refreshToken() != null) {
      builder.header("X-Refresh-Token", tokens.refreshToken());
    }

    HttpResponse<String> response;
    try {
      response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ProcessFailureException("The publish request to the Latte repository failed. Message was [" + e.getMessage() + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessFailureException("The publish request to the Latte repository was interrupted.");
    }

    String body = response.body();
    if (response.statusCode() != 200) {
      throw new ProcessFailureException(describeError(response.statusCode(), group, body));
    }

    String url;
    try {
      JSONObject json = (JSONObject) new JSONParser().parse(body);
      url = (String) json.get("url");
    } catch (ParseException | ClassCastException e) {
      throw new ProcessFailureException("The Latte repository returned an unparseable publish response. Body was [" + body + "]", e);
    }
    if (url == null) {
      throw new ProcessFailureException("The Latte repository publish response did not contain a URL. Body was [" + body + "]");
    }

    Tokens refreshed = null;
    String newAccessToken = response.headers().firstValue("X-Access-Token").orElse(null);
    if (newAccessToken != null) {
      String newRefreshToken = response.headers().firstValue("X-Refresh-Token").orElse(tokens.refreshToken());
      refreshed = new Tokens(newAccessToken, newRefreshToken);
    }

    return new PresignResponse(url, refreshed);
  }

  /**
   * Uploads the artifact bytes to a presigned PUT URL.
   *
   * @param presignedURL The presigned PUT URL returned by {@link #requestPresignedURL}.
   * @param body         The artifact bytes.
   * @throws ProcessFailureException If the upload returns anything other than a 200.
   */
  public void upload(String presignedURL, byte[] body) {
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(presignedURL))
                                     .header("Content-Type", "application/octet-stream")
                                     .timeout(Duration.ofSeconds(60))
                                     .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                                     .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ProcessFailureException("Uploading to the Latte repository failed. Message was [" + e.getMessage() + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessFailureException("The upload to the Latte repository was interrupted.");
    }

    if (response.statusCode() != 200) {
      throw new ProcessFailureException("Uploading to the Latte repository failed with HTTP [" + response.statusCode() + "]: " + response.body());
    }
  }

  /**
   * Verifies that the caller is permitted to publish to the given group using an authenticated HEAD request. This does
   * not request a presigned URL or change anything server-side; it only confirms the access token is valid and the
   * caller is an authorized publisher for the group.
   *
   * @param group  The artifact group to check publish permission for.
   * @param tokens The caller's current access and refresh tokens.
   * @return The readiness verdict and any tokens the server rotated during the request.
   */
  public PermissionResponse verifyPublishPermission(String group, Tokens tokens) {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
                                             .uri(URI.create(apiURL + "/api/v1/publish/" + group))
                                             .header("Authorization", "Bearer " + tokens.accessToken())
                                             .header("Accept", "application/json")
                                             .timeout(Duration.ofSeconds(30))
                                             .method("HEAD", HttpRequest.BodyPublishers.noBody());
    if (tokens.refreshToken() != null) {
      builder.header("X-Refresh-Token", tokens.refreshToken());
    }

    HttpResponse<String> response;
    try {
      response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new ProcessFailureException("The publish permission check to the Latte repository failed. Message was [" + e.getMessage() + "]", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProcessFailureException("The publish permission check to the Latte repository was interrupted.");
    }

    Tokens refreshed = null;
    String newAccessToken = response.headers().firstValue("X-Access-Token").orElse(null);
    if (newAccessToken != null) {
      String newRefreshToken = response.headers().firstValue("X-Refresh-Token").orElse(tokens.refreshToken());
      refreshed = new Tokens(newAccessToken, newRefreshToken);
    }

    PublishReadiness readiness = response.statusCode() == 200
        ? PublishReadiness.READY
        : PublishReadiness.notReady(describeError(response.statusCode(), group, response.body()));

    return new PermissionResponse(readiness, refreshed);
  }

  private void collectMessages(JSONArray errors, List<String> collected) {
    for (Object error : errors) {
      if (error instanceof JSONObject e && e.get("message") != null) {
        collected.add(e.get("message").toString());
      }
    }
  }

  /**
   * Builds a human-friendly message for a non-200 publish API response, tailored to the status code and enriched with
   * any error text the API returned.
   */
  private String describeError(int status, String group, String body) {
    return switch (status) {
      case 400 -> "The Latte repository rejected the request: " + messages(body);
      case 401 -> "Your Latte login has expired or is invalid. Run [latte login] and try again.";
      case 403 ->
          "You are not authorized to publish to the group [" + group + "]. The group must be verified and you must be an active owner or contributor." + serverDetail(body);
      case 404 -> "The Latte publish API was not found at [" + apiURL + "]. Check the configured [apiURL].";
      case 500 -> "The Latte repository encountered a server error. Please try again later.";
      case 503 -> "The Latte identity provider is temporarily unavailable. Please try again shortly.";
      default -> "Publishing to the Latte repository failed with HTTP [" + status + "]: " + body;
    };
  }

  /**
   * Extracts the best human-readable message(s) from an error body, handling both the validation {@code Errors} shape
   * ({@code fieldErrors}/{@code generalErrors}) and the simple {@code {error, message}} shape. Falls back to the raw
   * body when neither is present.
   */
  private String messages(String body) {
    try {
      JSONObject json = (JSONObject) new JSONParser().parse(body);

      List<String> collected = new ArrayList<>();
      Object fieldErrors = json.get("fieldErrors");
      if (fieldErrors instanceof JSONObject fields) {
        for (Object value : fields.values()) {
          if (value instanceof JSONArray errors) {
            collectMessages(errors, collected);
          }
        }
      }
      if (json.get("generalErrors") instanceof JSONArray generalErrors) {
        collectMessages(generalErrors, collected);
      }
      if (!collected.isEmpty()) {
        return String.join(" ", collected);
      }

      Object message = json.get("message");
      if (message != null) {
        return message.toString();
      }
    } catch (ParseException | ClassCastException e) {
      // Fall through to the raw body.
    }

    return body;
  }

  private String serverDetail(String body) {
    try {
      Object message = ((JSONObject) new JSONParser().parse(body)).get("message");
      if (message != null) {
        return " (server: " + message + ")";
      }
    } catch (ParseException | ClassCastException e) {
      // No usable detail.
    }
    return "";
  }

  /**
   * The result of a publish-permission check: the readiness verdict, and any tokens the server rotated during the
   * request.
   *
   * @param readiness       Whether the caller can publish to the group.
   * @param refreshedTokens The refreshed tokens, or {@code null} if the server did not refresh.
   */
  public record PermissionResponse(PublishReadiness readiness, Tokens refreshedTokens) {
  }

  /**
   * The result of a presigned-URL request: the URL to upload to, and any tokens the server rotated during the request.
   *
   * @param url             The presigned PUT URL.
   * @param refreshedTokens The refreshed tokens, or {@code null} if the server did not refresh.
   */
  public record PresignResponse(String url, Tokens refreshedTokens) {
  }
}
