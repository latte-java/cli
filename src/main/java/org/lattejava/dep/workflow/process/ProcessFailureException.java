/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import org.lattejava.dep.domain.ResolvableItem;

/**
 * Thrown when a process encounters a failure (network failure, IO exception, etc.).
 *
 * @author Brian Pontarelli
 */
public class ProcessFailureException extends RuntimeException {
  public ProcessFailureException(ResolvableItem item) {
    super("A process failed for the artifact [" + item + "].");
  }

  public ProcessFailureException(ResolvableItem item, Throwable cause) {
    super("A process failed for the artifact [" + item + "]." + (cause != null ? " The original error is [" + cause.getMessage() + "]\n" : "\n"), cause);
  }

  public ProcessFailureException(String message) {
    super(message);
  }

  public ProcessFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
