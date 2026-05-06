/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import java.util.Objects;

/**
 * Domain for license exceptions (like the classpath exception).
 *
 * @author Brian Pontarelli
 */
public class LicenseTextException {
  public String detailsURL;

  public String identifier;

  public String name;

  public String reference;

  public String text;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LicenseTextException)) return false;
    final LicenseTextException that = (LicenseTextException) o;
    return Objects.equals(identifier, that.identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier);
  }
}
