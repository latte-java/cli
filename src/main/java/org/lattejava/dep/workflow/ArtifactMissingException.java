/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow;

import org.lattejava.dep.domain.Artifact;

/**
 * Thrown when an artifact is missing.
 * <p>
 * This exception is not permanent and usually is fixed by changing to a different server. This exception should not
 * cause a negative cache file to be written and should repeat itself if nothing else changes.
 *
 * @author Brian Pontarelli
 */
public class ArtifactMissingException extends RuntimeException {
  public final Artifact artifact;

  public ArtifactMissingException(Artifact artifact) {
    super("The artifact [" + artifact + "] could not be located using your workflow");
    this.artifact = artifact;
  }
}
