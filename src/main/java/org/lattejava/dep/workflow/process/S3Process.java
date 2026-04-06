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
package org.lattejava.dep.workflow.process;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.lang.StringTools;
import org.lattejava.output.Output;
import org.lattejava.security.AWSV4Signer;
import org.lattejava.security.Algorithm;
import org.lattejava.security.Checksum;
import org.lattejava.security.ChecksumException;

/**
 * A workflow process that fetches and publishes artifacts using any S3-compatible object store (AWS S3, CloudFlare R2,
 * MinIO, Backblaze B2, etc.).
 * <p>
 * Objects are stored using the standard Latte layout:
 * <pre>
 *   {group}/{project}/{version}/{item}
 * </pre>
 * where group dots are replaced with slashes (e.g., {@code org.example} becomes {@code org/example}).
 *
 * @author Brian Pontarelli
 */
public class S3Process extends BaseProcess {
  private static final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(10_000))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public final String bucket;

  public final String endpoint;

  public final Output output;

  private final AWSV4Signer signer;

  /**
   * Creates a new S3Process.
   *
   * @param output          The output for logging.
   * @param endpoint        The S3 endpoint URL (e.g., {@code https://account-id.r2.cloudflarestorage.com}).
   * @param bucket          The bucket name.
   * @param accessKeyId     The S3 access key ID.
   * @param secretAccessKey The S3 secret access key.
   * @param region          The region (use {@code auto} for CloudFlare R2).
   */
  public S3Process(Output output, String endpoint, String bucket, String accessKeyId,
                   String secretAccessKey, String region) {
    this.output = output;
    this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    this.bucket = bucket;
    this.signer = new AWSV4Signer(accessKeyId, secretAccessKey, region);
  }

  /**
   * Applies signed headers to the request builder, filtering out the {@code host} header which Java's HttpClient
   * manages automatically from the URI.
   */
  private static void applyHeaders(Map<String, String> headers, HttpRequest.Builder requestBuilder) {
    headers.entrySet().stream()
        .filter(e -> !e.getKey().equalsIgnoreCase("host"))
        .forEach(e -> requestBuilder.header(e.getKey(), e.getValue()));
  }

  private static String objectKey(ResolvableItem item) {
    return objectKey(item, item.item);
  }

  private static String objectKey(ResolvableItem item, String itemName) {
    return item.group.replace('.', '/') + "/" + item.project + "/" + item.version + "/" + itemName;
  }

  @Override
  public FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    return fetchWithAlternatives(item, publishWorkflow);
  }

  @Override
  public Path publish(FetchResult fetchResult) throws ProcessFailureException {
    ResolvableItem item = fetchResult.item();
    String key = objectKey(item);
    Path file = fetchResult.file();

    try {
      byte[] body = Files.readAllBytes(file);
      put(key, body);
      output.infoln("Published [%s] to [s3://%s/%s]", item, bucket, key);
    } catch (IOException e) {
      throw new ProcessFailureException(item, e);
    }

    return null;
  }

  @Override
  public String toString() {
    return "S3(" + endpoint + "/" + bucket + ")";
  }

  protected FetchResult tryFetchCandidate(ResolvableItem item, String candidateItem, PublishWorkflow publishWorkflow)
      throws ProcessFailureException {
    try {
      // Fetch the checksum file first
      String checksumKey = objectKey(item, candidateItem + Algorithm.SHA256.extension);
      output.debugln("      - S3 GET [s3://%s/%s]", bucket, checksumKey);
      byte[] checksumBytes = get(checksumKey);
      if (checksumBytes == null) {
        output.debugln("      - Not found");
        return null;
      }

      Checksum checksum;
      try {
        String checksumStr = new String(checksumBytes, StandardCharsets.UTF_8).trim();
        checksum = new Checksum(checksumStr.substring(0, Algorithm.SHA256.hexLength),
            StringTools.fromHex(checksumStr.substring(0, Algorithm.SHA256.hexLength)),
            Algorithm.SHA256);
      } catch (Exception e) {
        throw new ProcessFailureException(item, e);
      }

      // Fetch the actual item
      String itemKey = objectKey(item, candidateItem);
      output.debugln("      - S3 GET [s3://%s/%s]", bucket, itemKey);
      byte[] itemBytes = get(itemKey);
      if (itemBytes == null) {
        output.debugln("      - Not found");
        return null;
      }

      // Verify checksum
      Checksum actual = Checksum.forBytes(itemBytes, Algorithm.SHA256);
      if (!actual.equals(checksum)) {
        throw new ChecksumException("SHA256 mismatch for [s3://" + bucket + "/" + itemKey + "]");
      }

      // Write to temp file
      Path tempFile = Files.createTempFile("latte-s3", "download");
      tempFile.toFile().deleteOnExit();
      Files.write(tempFile, itemBytes);

      output.infoln("Downloaded [s3://%s/%s]", bucket, itemKey);

      // Publish checksum to local cache
      Path checksumTempFile = Files.createTempFile("latte-s3", "checksum");
      checksumTempFile.toFile().deleteOnExit();
      Files.write(checksumTempFile, checksumBytes);
      ResolvableItem checksumItem = new ResolvableItem(item, candidateItem + Algorithm.SHA256.extension);
      publishWorkflow.publish(new FetchResult(checksumTempFile, ItemSource.LATTE, checksumItem));

      // Publish item to local cache
      ResolvableItem matchedItem = candidateItem.equals(item.item) ? item : new ResolvableItem(item, candidateItem);
      Path publishedFile = publishWorkflow.publish(new FetchResult(tempFile, ItemSource.LATTE, matchedItem));
      return new FetchResult(publishedFile != null ? publishedFile : tempFile, ItemSource.LATTE, matchedItem);
    } catch (ChecksumException e) {
      throw e;
    } catch (IOException e) {
      throw new ProcessFailureException(item, e);
    }
  }

  /**
   * Downloads an object from S3. Returns null if the object does not exist (404/403).
   */
  private byte[] get(String key) throws IOException {
    URI uri = URI.create(endpoint + "/" + bucket + "/" + key);
    Map<String, String> headers = new LinkedHashMap<>();
    signer.sign("GET", uri, headers, AWSV4Signer.EMPTY_BODY_SHA256);

    var requestBuilder = HttpRequest.newBuilder().uri(uri).GET().timeout(Duration.ofMillis(30_000));
    applyHeaders(headers, requestBuilder);

    HttpResponse<byte[]> response;
    try {
      response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("S3 GET interrupted for [" + uri + "]", e);
    }

    if (response.statusCode() == 404 || response.statusCode() == 403) {
      return null;
    }
    if (response.statusCode() != 200) {
      throw new IOException("S3 GET [" + uri + "] returned HTTP " + response.statusCode());
    }

    return response.body();
  }

  /**
   * Uploads an object to S3.
   */
  private void put(String key, byte[] body) throws IOException {
    URI uri = URI.create(endpoint + "/" + bucket + "/" + key);
    String payloadHash = AWSV4Signer.sha256Hex(body, 0, body.length);

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("content-type", "application/octet-stream");
    signer.sign("PUT", uri, headers, payloadHash);

    var requestBuilder = HttpRequest.newBuilder()
        .uri(uri)
        .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
        .timeout(Duration.ofMillis(60_000));
    applyHeaders(headers, requestBuilder);

    HttpResponse<String> response;
    try {
      response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("S3 PUT interrupted for [" + uri + "]", e);
    }

    if (response.statusCode() != 200) {
      throw new IOException("S3 PUT [" + uri + "] returned HTTP " + response.statusCode() +
          ": " + response.body());
    }
  }
}
