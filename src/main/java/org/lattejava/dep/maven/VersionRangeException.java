/*
 * Copyright (c) 2025-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.maven;

/**
 * Thrown when a POM contains a version range and the project did not provide a concreate mapping.
 *
 * @author Daniel DeGroff
 */
public class VersionRangeException extends RuntimeException {
  public VersionRangeException(String message) {
    super(message);
  }
}
