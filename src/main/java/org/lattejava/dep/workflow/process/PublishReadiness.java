/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

/**
 * The result of verifying whether a {@link Process} can publish a project's artifacts. A not-ready result carries a
 * human-readable message explaining why publishing would fail, so a release can be aborted with a useful error before
 * any irreversible step.
 *
 * @param ready   Whether the process is able to publish.
 * @param message The reason publishing would fail, or {@code null} when ready.
 *
 * @author Brian Pontarelli
 */
public record PublishReadiness(boolean ready, String message) {
  /**
   * A ready result with no message. This is a constant rather than a {@code ready()} factory, since the record already
   * auto-generates a {@code ready()} accessor for the boolean component.
   */
  public static final PublishReadiness READY = new PublishReadiness(true, null);

  /**
   * @param message The reason publishing would fail.
   * @return A not-ready result carrying the given message.
   */
  public static PublishReadiness notReady(String message) {
    return new PublishReadiness(false, message);
  }
}
