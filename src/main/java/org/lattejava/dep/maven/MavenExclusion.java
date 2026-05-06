/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.maven;

import java.util.Objects;

/**
 * A Maven dependency exclusion.
 *
 * @author Brian Pontarelli
 */
public class MavenExclusion {
  public String group;

  public String id;

  public MavenExclusion(String group, String id) {
    this.group = group;
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenExclusion)) return false;
    final MavenExclusion that = (MavenExclusion) o;
    return Objects.equals(group, that.group) && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(group, id);
  }
}
