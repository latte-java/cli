package org.lattejava.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class ChecksumTest extends BaseUnitTest {
  @Test
  public void sha256() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt");
    Checksum checksum = Checksum.sha256(f);
    assertNotNull(checksum);
    assertEquals(checksum.algorithm, Algorithm.SHA256);
    assertEquals(checksum.sum.length(), 64);
  }

  @Test
  public void sha1() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt");
    Checksum checksum = Checksum.forPath(f, Algorithm.SHA1);
    assertNotNull(checksum);
    assertEquals(checksum.algorithm, Algorithm.SHA1);
    assertEquals(checksum.sum.length(), 40);
  }

  @Test
  public void md5() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt");
    Checksum checksum = Checksum.md5(f);
    assertNotNull(checksum);
    assertEquals(checksum.algorithm, Algorithm.MD5);
    assertEquals(checksum.sum, "c0bfbec19e8e5578e458ce5bfee20751");
  }

  @Test
  public void loadBareHash() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();
    Checksum original = Checksum.sha256(projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt"));
    Files.writeString(tmp, original.sum);

    Checksum loaded = Checksum.load(tmp, Algorithm.SHA256);
    assertNotNull(loaded);
    assertEquals(loaded.sum, original.sum);
    assertEquals(loaded.algorithm, Algorithm.SHA256);
  }

  @Test
  public void loadHashWithFilename() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();
    Checksum original = Checksum.sha256(projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt"));
    Files.writeString(tmp, original.sum + "  some-file.txt\n");

    Checksum loaded = Checksum.load(tmp, Algorithm.SHA256);
    assertNotNull(loaded);
    assertEquals(loaded.sum, original.sum);
  }

  @Test
  public void loadInvalidHash() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();
    Files.writeString(tmp, "tooshort");

    try {
      Checksum.load(tmp, Algorithm.SHA256);
      fail("Should have thrown");
    } catch (ChecksumException e) {
      // Expected
    }
  }

  @Test
  public void loadNullPath() throws IOException {
    assertNull(Checksum.load(null, Algorithm.SHA256));
  }

  @Test
  public void writeAndLoad() throws IOException {
    Path tmp = Files.createTempFile("checksum-test", ".sha256");
    tmp.toFile().deleteOnExit();

    Checksum original = Checksum.sha256(projectDir.resolve("src/test/java/org/lattejava/security/MD5Test.txt"));
    Checksum.write(original, tmp);

    Checksum loaded = Checksum.load(tmp, Algorithm.SHA256);
    assertNotNull(loaded);
    assertEquals(loaded.sum, original.sum);
  }

  @Test
  public void streamingVerify() throws IOException {
    byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    Checksum expected = Checksum.forBytes(data, Algorithm.SHA256);

    ByteArrayInputStream bais = new ByteArrayInputStream(data);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Checksum result = ChecksumTools.write(bais, baos, expected);

    assertNotNull(result);
    assertEquals(result.sum, expected.sum);
    assertEquals(baos.toByteArray(), data);
  }

  @Test
  public void streamingVerifyMismatch() throws IOException {
    byte[] data = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    Checksum bad = new Checksum("0000000000000000000000000000000000000000000000000000000000000000", new byte[32], Algorithm.SHA256);

    try {
      ChecksumTools.write(new ByteArrayInputStream(data), new ByteArrayOutputStream(), bad);
      fail("Should have thrown");
    } catch (ChecksumException e) {
      // Expected
    }
  }
}
