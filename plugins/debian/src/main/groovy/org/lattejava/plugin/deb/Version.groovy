/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

import java.util.regex.Pattern

/**
 * Version for the debian package.
 *
 * @author Brian Pontarelli
 */
class Version {
  public static final Pattern UPSTREAM_VERSION_PATTERN = ~/[0-9][A-Za-z0-9.+\-:~]*/

  public static final Pattern DEBIAN_VERSION_PATTERN = ~/[A-Za-z0-9+.~]+/

  public String upstream

  public String debian

  Version(String upstream, String debian) {
    this.upstream = upstream
    this.debian = debian != null ? debian : "1"
  }

  boolean isDebianValid() {
    return DEBIAN_VERSION_PATTERN.asPredicate().test(debian)
  }

  boolean isUpstreamValid() {
    return UPSTREAM_VERSION_PATTERN.asPredicate().test(upstream) && (debian != null || !upstream.contains("-"))
  }

  public String toString() {
    StringBuilder version = new StringBuilder()
    version.append(upstream)

    if (debian.length() > 0) {
      version.append('-')
      version.append(debian)
    }

    return version.toString()
  }
}
