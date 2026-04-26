/*
 * Copyright (c) 2026, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.security;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.lattejava.lang.StringTools;

/**
 * Implements AWS Signature Version 4 request signing for S3-compatible APIs.
 *
 * @author Brian Pontarelli
 */
public class AWSV4Signer {
  public static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

  private final String accessKeyId;

  private final String region;

  private final String secretAccessKey;

  public AWSV4Signer(String accessKeyId, String secretAccessKey, String region) {
    this.accessKeyId = accessKeyId;
    this.secretAccessKey = secretAccessKey;
    this.region = region;
  }

  /**
   * Signs an HTTP request for an S3-compatible API using AWS Signature V4, using the current UTC time.
   *
   * @param method      The HTTP method (GET, PUT, HEAD, DELETE).
   * @param uri         The full request URI.
   * @param headers     Mutable map of headers. This method adds Host, x-amz-date, x-amz-content-sha256, and
   *                    Authorization. Any headers already present (e.g., Content-Type) are included in the signature.
   * @param payloadHash The SHA-256 hex digest of the request body, or {@link #EMPTY_BODY_SHA256} for empty bodies.
   */
  public void sign(String method, URI uri, Map<String, String> headers, String payloadHash) {
    sign(method, uri, headers, payloadHash, ZonedDateTime.now(ZoneOffset.UTC));
  }

  /**
   * Signs an HTTP request for an S3-compatible API using AWS Signature V4 with an explicit timestamp. This overload is
   * useful for testing with known date/time values.
   *
   * @param method      The HTTP method (GET, PUT, HEAD, DELETE).
   * @param uri         The full request URI.
   * @param headers     Mutable map of headers. This method adds Host, x-amz-date, x-amz-content-sha256, and
   *                    Authorization. Any headers already present (e.g., Content-Type) are included in the signature.
   * @param payloadHash The SHA-256 hex digest of the request body, or {@link #EMPTY_BODY_SHA256} for empty bodies.
   * @param now         The timestamp to use for signing.
   */
  public void sign(String method, URI uri, Map<String, String> headers, String payloadHash, ZonedDateTime now) {
    String dateTime = DATETIME_FORMAT.format(now);
    String date = DATE_FORMAT.format(now);

    headers.put("host", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
    headers.put("x-amz-date", dateTime);
    headers.put("x-amz-content-sha256", payloadHash);

    // Canonical headers — sorted by lowercase key
    TreeMap<String, String> sorted = new TreeMap<>();
    for (var entry : headers.entrySet()) {
      sorted.put(entry.getKey().toLowerCase(), entry.getValue().trim());
    }

    String canonicalHeaders = sorted.entrySet().stream()
                                    .map(e -> e.getKey() + ":" + e.getValue() + "\n")
                                    .collect(Collectors.joining());
    String signedHeaders = String.join(";", sorted.keySet());

    // Canonical request
    String canonicalRequest = String.join("\n",
        method,
        canonicalPath(uri),
        canonicalQueryString(uri),
        canonicalHeaders,
        signedHeaders,
        payloadHash
    );

    String scope = date + "/" + region + "/s3/aws4_request";
    String stringToSign = String.join("\n",
        "AWS4-HMAC-SHA256",
        dateTime,
        scope,
        sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8))
    );

    byte[] signingKey = deriveSigningKey(date);
    String signature = StringTools.toHex(hmac(signingKey, stringToSign));

    headers.put("Authorization",
        "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope +
            ", SignedHeaders=" + signedHeaders +
            ", Signature=" + signature);
  }

  private static String canonicalPath(URI uri) {
    String path = uri.getRawPath();
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path;
  }

  private static String canonicalQueryString(URI uri) {
    String query = uri.getRawQuery();
    if (query == null || query.isEmpty()) {
      return "";
    }

    TreeMap<String, String> params = new TreeMap<>();
    for (String pair : query.split("&")) {
      int eq = pair.indexOf('=');
      String key = eq >= 0 ? pair.substring(0, eq) : pair;
      String value = eq >= 0 ? pair.substring(eq + 1) : "";
      params.put(URLEncoder.encode(key, StandardCharsets.UTF_8),
          URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
    return params.entrySet().stream()
                 .map(e -> e.getKey() + "=" + e.getValue())
                 .collect(Collectors.joining("&"));
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 computation failed", e);
    }
  }

  private static String sha256Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return StringTools.toHex(digest.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /**
   * Computes the SHA-256 hex digest of the given bytes. Useful for callers that need to provide the payload hash.
   *
   * @param data The data to hash.
   * @return The lowercase hex-encoded SHA-256 digest.
   */
  public static String sha256Hex(byte[] data, int offset, int length) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(data, offset, length);
      return StringTools.toHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private byte[] deriveSigningKey(String date) {
    byte[] dateKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
    byte[] regionKey = hmac(dateKey, region);
    byte[] serviceKey = hmac(regionKey, "s3");
    return hmac(serviceKey, "aws4_request");
  }
}
