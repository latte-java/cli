/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.parser;

import org.lattejava.util.Graph;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.domain.Target;

/**
 * Builds a Graph of target dependencies.
 *
 * @author Brian Pontarelli
 */
public interface TargetGraphBuilder {
  /**
   * Builds a graph of target dependencies for the given project.
   *
   * @param project The project.
   * @return THe graph.
   */
  Graph<Target, Object> build(Project project);
}
