/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import org.lattejava.dep.graph.DependencyGraph;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.lattejava.domain.Version;

/**
 * An exception that is thrown when a Version string cannot be parsed.
 *
 * @author Brian Pontarelli
 */
public class CompatibilityException extends RuntimeException {
  public final DependencyGraph graph;
  public final Dependency dependency;
  public final Version min;
  public final Version max;

  public CompatibilityException(DependencyGraph graph, Dependency dependency, Version min, Version max) {
    super("The artifact [" + dependency.id + "] has incompatible versions in your dependencies. The versions are [" + min + ", " + max + "]");
    this.graph = graph;
    this.dependency = dependency;
    this.min = min;
    this.max = max;
  }
}
