/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
public class Hello {
  public static void main(String[] args) throws Exception {
    String markerPath = System.getProperty("latte.run.marker");
    if (markerPath == null) {
      throw new IllegalStateException("latte.run.marker system property is required");
    }
    java.nio.file.Files.writeString(java.nio.file.Path.of(markerPath), "hello from source file");
  }
}
