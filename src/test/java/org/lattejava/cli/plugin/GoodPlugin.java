/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.plugin;

import org.lattejava.cli.domain.Project;
import org.lattejava.output.Output;
import org.lattejava.cli.runtime.RuntimeConfiguration;

/**
 * Sample plugin.
 *
 * @author Brian Pontarelli
 */
public class GoodPlugin implements Plugin {
  public final Output output;

  public final Project project;

  public final RuntimeConfiguration runtimeConfiguration;

  public GoodPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    this.project = project;
    this.output = output;
    this.runtimeConfiguration = runtimeConfiguration;
  }
}
