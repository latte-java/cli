package org.lattejava.security;

public enum Algorithm {
  SHA256("SHA-256", 64, ".sha256"),
  SHA1("SHA-1", 40, ".sha1"),
  MD5("MD5", 32, ".md5");

  public final String digestName;
  public final int hexLength;
  public final String extension;

  Algorithm(String digestName, int hexLength, String extension) {
    this.digestName = digestName;
    this.hexLength = hexLength;
    this.extension = extension;
  }
}
