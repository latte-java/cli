/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep;

import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ResolvedArtifact;

/**
 * This interface is a listener that is notified when the {@link DependencyService} fetches and publishes artifacts.
 *
 * @author Brian Pontarelli
 */
public interface DependencyListener {
  /**
   * Handle when an artifact is fetched by a mediator.
   *
   * @param artifact The artifact fetched.
   */
  void artifactFetched(ResolvedArtifact artifact);

  /**
   * Handle when an artifact is published by a mediator.
   *
   * @param artifact The artifact being published.
   */
  void artifactPublished(Artifact artifact);
}
