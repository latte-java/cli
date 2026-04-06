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

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.output.Output;
import org.lattejava.output.SystemOutOutput;
import org.lattejava.security.AWSV4Signer;
import org.lattejava.security.Algorithm;
import org.lattejava.security.Checksum;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the S3Process against a local MinIO Docker container.
 * <p>
 * Before running these tests, start MinIO:
 * <pre>
 *   docker run -d --name latte-minio \
 *     -p 9000:9000 -p 9001:9001 \
 *     -e MINIO_ROOT_USER=latte-test \
 *     -e MINIO_ROOT_PASSWORD=latte-test-secret \
 *     minio/minio server /data --console-address ":9001"
 * </pre>
 * Then create the test bucket:
 * <pre>
 *   docker exec latte-minio mc alias set local http://localhost:9000 latte-test latte-test-secret
 *   docker exec latte-minio mc mb local/latte-test
 * </pre>
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class S3ProcessTest {
  private static final String ACCESS_KEY = "latte-test";

  private static final String BUCKET = "latte-test";

  private static final String ENDPOINT = "http://localhost:9000";

  private static final String REGION = "us-east-1";

  private static final String SECRET_KEY = "latte-test-secret";

  private Output output;

  private S3Process s3;

  @BeforeClass
  public void beforeClass() {
    output = new SystemOutOutput(true);
    output.enableDebug();

    // Verify MinIO is running
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      HttpResponse<String> response = client.send(
          HttpRequest.newBuilder().uri(URI.create(ENDPOINT + "/minio/health/live")).GET()
              .timeout(Duration.ofSeconds(2)).build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("MinIO returned HTTP " + response.statusCode());
      }
    } catch (Exception e) {
      throw new RuntimeException("""
          MinIO is not running. Start it with:

            docker run -d --name latte-minio \\
              -p 9000:9000 -p 9001:9001 \\
              -e MINIO_ROOT_USER=latte-test \\
              -e MINIO_ROOT_PASSWORD=latte-test-secret \\
              minio/minio server /data --console-address ":9001"

          Then create the test bucket:

            docker exec latte-minio mc alias set local http://localhost:9000 latte-test latte-test-secret
            docker exec latte-minio mc mb local/latte-test
          """, e);
    }

    s3 = new S3Process(output, ENDPOINT, BUCKET, ACCESS_KEY, SECRET_KEY, REGION);
  }

  @Test
  public void publishAndFetch() throws Exception {
    // Create a temp file with known content
    byte[] content = "Hello from Latte S3 test!".getBytes(StandardCharsets.UTF_8);
    Path tempFile = Files.createTempFile("latte-s3-test", ".jar");
    tempFile.toFile().deleteOnExit();
    Files.write(tempFile, content);

    // Create the checksum file and publish it first so fetch can find it
    Checksum checksum = Checksum.forBytes(content, Algorithm.SHA256);
    Path checksumFile = Files.createTempFile("latte-s3-test", ".sha256");
    checksumFile.toFile().deleteOnExit();
    Files.writeString(checksumFile, checksum.sum);

    ResolvableItem item = new ResolvableItem("org.lattejava.test", "s3-test", "s3-test", "1.0.0",
        "s3-test-1.0.0.jar");
    ResolvableItem checksumItem = new ResolvableItem(item, "s3-test-1.0.0.jar.sha256");

    // Publish the checksum, then the artifact
    s3.publish(new FetchResult(checksumFile, ItemSource.LATTE, checksumItem));
    s3.publish(new FetchResult(tempFile, ItemSource.LATTE, item));

    // Fetch it back using a CacheProcess as the publish target
    Path cacheDir = Files.createTempDirectory("latte-s3-cache");
    cacheDir.toFile().deleteOnExit();
    CacheProcess cache = new CacheProcess(output, cacheDir.toString(), null, null);
    PublishWorkflow publishWorkflow = new PublishWorkflow(cache);

    FetchResult result = s3.fetch(item, publishWorkflow);
    assertNotNull(result, "Fetch should have found the artifact");
    assertTrue(Files.isRegularFile(result.file()), "Fetched file should exist");
    assertEquals(Files.readAllBytes(result.file()), content, "Fetched content should match");
    assertEquals(result.source(), ItemSource.LATTE);
  }

  @Test
  public void fetchNotFound() throws Exception {
    ResolvableItem item = new ResolvableItem("org.lattejava.test", "does-not-exist", "does-not-exist", "9.9.9",
        "does-not-exist-9.9.9.jar");

    Path cacheDir = Files.createTempDirectory("latte-s3-cache");
    cacheDir.toFile().deleteOnExit();
    CacheProcess cache = new CacheProcess(output, cacheDir.toString(), null, null);
    PublishWorkflow publishWorkflow = new PublishWorkflow(cache);

    FetchResult result = s3.fetch(item, publishWorkflow);
    assertNull(result, "Fetch should return null for non-existent artifact");
  }

  @Test
  public void publishOverwrite() throws Exception {
    ResolvableItem item = new ResolvableItem("org.lattejava.test", "s3-overwrite", "s3-overwrite", "1.0.0",
        "s3-overwrite-1.0.0.jar");

    // Publish version 1
    byte[] content1 = "version 1".getBytes(StandardCharsets.UTF_8);
    Path tempFile1 = Files.createTempFile("latte-s3-test", ".jar");
    tempFile1.toFile().deleteOnExit();
    Files.write(tempFile1, content1);
    s3.publish(new FetchResult(tempFile1, ItemSource.LATTE, item));

    // Publish version 2 (same key, different content)
    byte[] content2 = "version 2".getBytes(StandardCharsets.UTF_8);
    Path tempFile2 = Files.createTempFile("latte-s3-test", ".jar");
    tempFile2.toFile().deleteOnExit();
    Files.write(tempFile2, content2);
    s3.publish(new FetchResult(tempFile2, ItemSource.LATTE, item));

    // Publish the checksum for version 2 so fetch works
    Checksum checksum = Checksum.forBytes(content2, Algorithm.SHA256);
    Path checksumFile = Files.createTempFile("latte-s3-test", ".sha256");
    checksumFile.toFile().deleteOnExit();
    Files.writeString(checksumFile, checksum.sum);
    ResolvableItem checksumItem = new ResolvableItem(item, "s3-overwrite-1.0.0.jar.sha256");
    s3.publish(new FetchResult(checksumFile, ItemSource.LATTE, checksumItem));

    // Fetch should return version 2
    Path cacheDir = Files.createTempDirectory("latte-s3-cache");
    cacheDir.toFile().deleteOnExit();
    CacheProcess cache = new CacheProcess(output, cacheDir.toString(), null, null);
    PublishWorkflow publishWorkflow = new PublishWorkflow(cache);

    FetchResult result = s3.fetch(item, publishWorkflow);
    assertNotNull(result);
    assertEquals(Files.readAllBytes(result.file()), content2, "Should get the latest version");
  }
}
