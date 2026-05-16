/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

import java.nio.file.Path

/**
 * Change log file for the debian package.
 *
 * @author Brian Pontarelli
 */
class ChangeLog {
  public static final String STANDARD_FILENAME = "changelog.gz"

  public static final String DEBIAN_FILENAME = "changelog.Debian.gz"

  public Path file

  public boolean debian

  ChangeLog(Path file, Boolean debian) {
    this.file = file
    this.debian = debian != null ? debian : false
  }

  public String getTargetFilename() {
    return debian ? DEBIAN_FILENAME : STANDARD_FILENAME
  }
}
