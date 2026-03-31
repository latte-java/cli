/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
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

/**
 * This class is a simple holder for a MD5 checksum. It holds the sum and the file name. It can also hold the MD5 sum
 * bytes.
 *
 * @author Brian Pontarelli
 */
public final class MD5 {
  public final byte[] bytes;

  public final String fileName;

  public final String sum;

  public MD5(String sum, byte[] bytes, String fileName) {
    this.sum = sum;
    this.bytes = bytes;
    this.fileName = fileName;
  }

  /**
   * Calculates the MD5 for the given bytes. This optionally takes a file name, which isn't required, but can be useful
   * when calculating MD5s for files.
   *
   * @param bytes    The bytes.
   * @param fileName (Optional) The file name.
   * @return The MD5 and never null.
   * @throws IOException If the MD5 fails for any reason.
   */
  public static MD5 forBytes(byte[] bytes, String fileName) throws IOException {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("MD5");
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      System.err.println("Unable to locate MD5 algorithm");
      System.exit(1);
    }

    // Read in the file in blocks while doing the MD5 sum
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    DigestInputStream dis = new DigestInputStream(bais, digest);

    byte[] ba = new byte[1024];
    while (dis.read(ba, 0, 1024) != -1) {
      // Gobble, gobble
    }

    dis.close();

    byte[] md5 = digest.digest();
    return new MD5(StringTools.toHex(md5), md5, fileName);
  }

  /**
   * Calculates the MD5 sum for the given Path.
   *
   * @param path The path to MD5.
   * @return The MD5 sum and never null.
   * @throws IOException If the file could not be MD5 summed.
   */
  public static MD5 forPath(Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("File to MD5 doesn't exist [" + path.toAbsolutePath() + "]");
    }

    return MD5.forBytes(Files.readAllBytes(path), path.getFileName().toString());
  }

  /**
   * Loads the MD5 file at the given Path. This doesn't calculate the MD5 for the given path. The given path must be an
   * MD5 file.
   *
   * @param path The path to parse the MD5 sum from.
   * @return The MD5.
   * @throws IOException If the MD5 file is not a valid MD5 file or was unreadable.
   */
  public static MD5 load(Path path) throws IOException {
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }

    String str = new String(Files.readAllBytes(path), "UTF-8").trim();

    // Validate format (should be either only the md5sum or the sum plus the file name)
    if (str.length() < 32) {
      throw new MD5Exception("Invalid MD5 [" + str + "] in file [" + path + "]");
    }

    String name = null;
    String sum;
    if (str.length() == 32) {
      sum = str;
    } else if (str.length() > 33) {
      int index = str.indexOf(" ");
      if (index == 32) {
        sum = str.substring(0, 32);

        // Find file name and verify
        while (str.charAt(index) == ' ') {
          index++;
        }

        if (index == str.length()) {
          throw new MD5Exception("Invalid MD5 [" + str + "] in file [" + path + "]");
        }

        name = str.substring(index);
      } else {
        throw new MD5Exception("Invalid MD5 [" + str + "] in file [" + path + "]");
      }
    } else {
      throw new MD5Exception("Invalid MD5 [" + str + "] in file [" + path + "]. It has a length of [" + str.length() + "] and it should be 32");
    }

    return new MD5(sum, StringTools.fromHex(sum), name);
  }

  /**
   * Writes the MD5 information out to the given Path file.
   *
   * @param md5  The MD5.
   * @param path The path to write the MD5 to.
   * @throws IOException If the write fails.
   */
  public static void writeMD5(MD5 md5, Path path) throws IOException {
    String sum = md5.sum;
    if (!sum.endsWith("\n")) {
      sum += "\n";
    }

    Files.write(path, sum.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MD5 md5 = (MD5) o;
    return Arrays.equals(bytes, md5.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
