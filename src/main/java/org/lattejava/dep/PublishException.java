/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep;

import org.lattejava.dep.domain.Publication;

/**
 * Thrown when publishing an Artifact fails.
 *
 * @author Brian Pontarelli
 */
public class PublishException extends RuntimeException {
  public PublishException(Publication publication, Throwable cause) {
    super("Unable to publish the publication [" + publication + "] because an IO error occurred.", cause);
  }

  public PublishException(String message) {
    super(message);
  }
}
