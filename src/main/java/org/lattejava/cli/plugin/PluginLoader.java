/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.plugin;

import org.lattejava.dep.domain.Artifact;

/**
 * Defines the method used to load Plugins
 *
 * @author Brian Pontarelli
 */
public interface PluginLoader {
  /**
   * Loads the plugin with the given dependency.
   *
   * @param pluginDependency The dependency definition of the plugin.
   * @return The Plugin instance.
   */
  Plugin load(Artifact pluginDependency);
}
