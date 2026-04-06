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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the AWSV4Signer using the AWS Signature Version 4 test suite values.
 * <p>
 * Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class AWSV4SignerTest {
  // AWS test suite credentials
  private static final String ACCESS_KEY = "AKIAIOSFODNN7EXAMPLE";
  private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
  private static final String REGION = "us-east-1";
  private static final ZonedDateTime TEST_TIME = ZonedDateTime.of(2013, 5, 24, 0, 0, 0, 0, ZoneOffset.UTC);

  /**
   * Tests GET Object — the primary example from the AWS S3 SigV4 documentation.
   * <p>
   * Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
   * "Example: GET Object"
   */
  @Test
  public void getObject() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://examplebucket.s3.amazonaws.com/test.txt");

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("range", "bytes=0-9");
    signer.sign("GET", uri, headers, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    String auth = headers.get("Authorization");
    assertNotNull(auth);
    assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential="));

    // Verify the Authorization header structure
    assertTrue(auth.contains("Credential=" + ACCESS_KEY + "/20130524/us-east-1/s3/aws4_request"));
    assertTrue(auth.contains("SignedHeaders="));
    assertTrue(auth.contains("Signature="));

    // Verify the expected signature from the AWS documentation
    assertTrue(auth.contains("Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"));
  }

  /**
   * Tests PUT Object — verifies the signing process produces a valid Authorization header with the correct structure
   * and that the same inputs always produce the same signature.
   */
  @Test
  public void putObject() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://examplebucket.s3.amazonaws.com/test%24file.text");

    byte[] payload = "Welcome to Amazon S3.".getBytes();
    String payloadHash = AWSV4Signer.sha256Hex(payload, 0, payload.length);
    assertEquals(payloadHash, "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072");

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("date", "Fri, 24 May 2013 00:00:00 GMT");
    headers.put("x-amz-storage-class", "REDUCED_REDUNDANCY");
    signer.sign("PUT", uri, headers, payloadHash, TEST_TIME);

    String auth = headers.get("Authorization");
    assertNotNull(auth);
    assertTrue(auth.contains("Credential=" + ACCESS_KEY + "/20130524/us-east-1/s3/aws4_request"));
    assertTrue(auth.contains("SignedHeaders=date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class"));
    assertTrue(auth.contains("Signature="));

    // Verify deterministic: same inputs produce the same signature
    Map<String, String> headers2 = new LinkedHashMap<>();
    headers2.put("date", "Fri, 24 May 2013 00:00:00 GMT");
    headers2.put("x-amz-storage-class", "REDUCED_REDUNDANCY");
    signer.sign("PUT", uri, headers2, payloadHash, TEST_TIME);
    assertEquals(headers.get("Authorization"), headers2.get("Authorization"));

    // Verify the AWS documented signature for the PUT Object example
    // Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
    assertTrue(auth.contains("Signature=98ad721746da40c64f1a55b78f14c238d841ea1380cd77a1b5971af0ece108bd"),
        "Actual Authorization: " + auth);
  }

  /**
   * Tests GET Bucket Lifecycle — uses query parameters.
   * <p>
   * Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
   * "Example: GET Bucket Lifecycle"
   */
  @Test
  public void getBucketLifecycle() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://examplebucket.s3.amazonaws.com/?lifecycle");

    Map<String, String> headers = new LinkedHashMap<>();
    signer.sign("GET", uri, headers, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    String auth = headers.get("Authorization");
    assertNotNull(auth);
    assertTrue(auth.contains("Credential=" + ACCESS_KEY + "/20130524/us-east-1/s3/aws4_request"));
    assertTrue(auth.contains("Signature=fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543"));
  }

  /**
   * Tests that the empty body SHA256 constant is correct.
   */
  @Test
  public void emptyBodySha256() {
    String computed = AWSV4Signer.sha256Hex(new byte[0], 0, 0);
    assertEquals(computed, AWSV4Signer.EMPTY_BODY_SHA256);
  }

  /**
   * Tests that sha256Hex produces the correct hash for known input.
   */
  @Test
  public void sha256Hex() {
    // SHA-256 of "Welcome to Amazon S3." from the AWS documentation
    byte[] payload = "Welcome to Amazon S3.".getBytes();
    String hash = AWSV4Signer.sha256Hex(payload, 0, payload.length);
    assertEquals(hash, "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072");
  }

  /**
   * Tests signing with a custom port in the URI.
   */
  @Test
  public void customPort() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://my-bucket.s3.amazonaws.com:8443/my-key");

    Map<String, String> headers = new LinkedHashMap<>();
    signer.sign("GET", uri, headers, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    // Verify the host header includes the port
    assertEquals(headers.get("host"), "my-bucket.s3.amazonaws.com:8443");

    // Verify it produced a valid Authorization header
    String auth = headers.get("Authorization");
    assertNotNull(auth);
    assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential="));
  }

  /**
   * Tests that signing populates the required headers.
   */
  @Test
  public void requiredHeaders() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://my-bucket.s3.amazonaws.com/my-key");

    Map<String, String> headers = new LinkedHashMap<>();
    signer.sign("GET", uri, headers, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    assertNotNull(headers.get("host"));
    assertNotNull(headers.get("x-amz-date"));
    assertNotNull(headers.get("x-amz-content-sha256"));
    assertNotNull(headers.get("Authorization"));
    assertEquals(headers.get("x-amz-date"), "20130524T000000Z");
    assertEquals(headers.get("x-amz-content-sha256"), AWSV4Signer.EMPTY_BODY_SHA256);
  }

  /**
   * Tests that the same signer can be reused for multiple requests (statelessness).
   */
  @Test
  public void signerReuse() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://my-bucket.s3.amazonaws.com/key1");

    Map<String, String> headers1 = new LinkedHashMap<>();
    signer.sign("GET", uri, headers1, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    Map<String, String> headers2 = new LinkedHashMap<>();
    signer.sign("GET", uri, headers2, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    // Same inputs should produce same signature
    assertEquals(headers1.get("Authorization"), headers2.get("Authorization"));
  }

  /**
   * Tests GET with List Objects (query parameter with value).
   * <p>
   * Reference: https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-header-based-auth.html
   * "Example: GET Bucket (List Objects)"
   */
  @Test
  public void getBucketListObjects() {
    AWSV4Signer signer = new AWSV4Signer(ACCESS_KEY, SECRET_KEY, REGION);
    URI uri = URI.create("https://examplebucket.s3.amazonaws.com/?max-keys=2&prefix=J");

    Map<String, String> headers = new LinkedHashMap<>();
    signer.sign("GET", uri, headers, AWSV4Signer.EMPTY_BODY_SHA256, TEST_TIME);

    String auth = headers.get("Authorization");
    assertNotNull(auth);
    assertTrue(auth.contains("Credential=" + ACCESS_KEY + "/20130524/us-east-1/s3/aws4_request"));
    assertTrue(auth.contains("Signature=34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7"));
  }
}
