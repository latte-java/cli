/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain.json;

public class AMDJsonLicense {
  public String type;

  public String text;

  public AMDJsonLicense() {
  }

  public AMDJsonLicense(String type, String text) {
    this.type = type;
    this.text = text;
  }
}
