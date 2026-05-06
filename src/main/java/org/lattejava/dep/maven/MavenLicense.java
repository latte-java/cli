/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.maven;

import java.util.Objects;

public class MavenLicense {
  public String distribution;

  public String name;

  public String url;

  public MavenLicense() {
  }

  public MavenLicense(String distribution, String name, String url) {
    this.distribution = distribution;
    this.name = name;
    this.url = url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenLicense)) return false;
    final MavenLicense that = (MavenLicense) o;
    return Objects.equals(distribution, that.distribution) && Objects.equals(name, that.name) && Objects.equals(url, that.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(distribution, name, url);
  }
}
