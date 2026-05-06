/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep;

import org.lattejava.dep.domain.ReifiedArtifact;

/**
 * Thrown when an invalid license is encountered during the resolution process.
 *
 * @author Brian Pontarelli
 */
public class LicenseException extends RuntimeException {
  public final ReifiedArtifact artifact;

  public LicenseException(String id) {
    super("[" + id + "] is an invalid license or is missing the license text");
    this.artifact = null;
  }

  public LicenseException(ReifiedArtifact artifact) {
    super("The artifact [" + artifact + "] uses an invalid license " + artifact.licenses);
    this.artifact = artifact;
  }
}
