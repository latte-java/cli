/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

/**
 * The maintainer of the Debian package.
 *
 * @author Brian Pontarelli
 */
class Maintainer {
  public String email

  public String name

  Maintainer(String name, String email) {
    this.email = email
    this.name = name
  }

  public String toString() {
    if (name == null || name.length() == 0) {
      return email
    }

    return "${name} <${email}>"
  }
}
