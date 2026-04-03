package org.lattejava.security;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.lattejava.lang.StringTools;

public class ChecksumTools {
  public static Checksum write(InputStream is, OutputStream os, Checksum checksum) throws IOException {
    Algorithm algorithm = checksum != null ? checksum.algorithm : Algorithm.SHA256;
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(algorithm.digestName);
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to locate " + algorithm.digestName + " algorithm");
    }

    DigestInputStream inputStream = new DigestInputStream(new BufferedInputStream(is), digest);
    inputStream.on(true);

    try (BufferedOutputStream bof = new BufferedOutputStream(os)) {
      byte[] b = new byte[8192];
      int len;
      while ((len = inputStream.read(b)) != -1) {
        bof.write(b, 0, len);
      }
    }

    byte[] hash = inputStream.getMessageDigest().digest();

    if (checksum != null && checksum.bytes != null) {
      if (!Arrays.equals(hash, checksum.bytes)) {
        throw new ChecksumException(algorithm.name() + " mismatch when writing from the InputStream to the OutputStream. Expected [" + StringTools.toHex(checksum.bytes) + "] but was [" + StringTools.toHex(hash) + "]");
      }
    }

    return new Checksum(StringTools.toHex(hash), hash, algorithm);
  }
}
