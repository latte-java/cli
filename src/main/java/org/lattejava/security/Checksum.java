package org.lattejava.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.lattejava.lang.StringTools;

public final class Checksum {
  public final Algorithm algorithm;
  public final byte[] bytes;
  public final String sum;

  public Checksum(String sum, byte[] bytes, Algorithm algorithm) {
    this.sum = sum;
    this.bytes = bytes;
    this.algorithm = algorithm;
  }

  public static Checksum sha256(Path path) throws IOException {
    return forPath(path, Algorithm.SHA256);
  }

  public static Checksum sha1(Path path) throws IOException {
    return forPath(path, Algorithm.SHA1);
  }

  public static Checksum md5(Path path) throws IOException {
    return forPath(path, Algorithm.MD5);
  }

  public static Checksum forBytes(byte[] bytes, Algorithm algorithm) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm.digestName);
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to locate " + algorithm.digestName + " algorithm");
    }

    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    DigestInputStream dis = new DigestInputStream(bais, digest);
    byte[] ba = new byte[1024];
    while (dis.read(ba, 0, 1024) != -1) {
    }
    dis.close();

    byte[] hash = digest.digest();
    return new Checksum(StringTools.toHex(hash), hash, algorithm);
  }

  public static Checksum forPath(Path path, Algorithm algorithm) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("File to checksum doesn't exist [" + path.toAbsolutePath() + "]");
    }
    return forBytes(Files.readAllBytes(path), algorithm);
  }

  public static Checksum load(Path path, Algorithm algorithm) throws IOException {
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }

    String str = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    if (str.length() < algorithm.hexLength) {
      throw new ChecksumException("Invalid " + algorithm.name() + " checksum [" + str + "] in file [" + path + "]. Expected at least " + algorithm.hexLength + " hex characters");
    }

    String sum = str.substring(0, algorithm.hexLength);
    return new Checksum(sum, StringTools.fromHex(sum), algorithm);
  }

  public static void write(Checksum checksum, Path path) throws IOException {
    String sum = checksum.sum;
    if (!sum.endsWith("\n")) {
      sum += "\n";
    }
    Files.write(path, sum.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Checksum checksum = (Checksum) o;
    return Arrays.equals(bytes, checksum.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
