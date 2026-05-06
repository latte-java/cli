/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.plugin;

import org.lattejava.cli.domain.Project;
import org.lattejava.output.Output;
import org.lattejava.cli.runtime.RuntimeConfiguration;

/**
 * <p>
 * Defines a Plugin for the Latte build system. This is a marker interface that allows Plugins to be written in any
 * language. The only requirements of Plugins is that they must have a single constructor that takes a {@link Project},
 * {@link RuntimeConfiguration} and a {@link Output} (in that order). For example:
 * </p>
 * <pre>
 *   class GroovyPlugin {
 *     GroovyPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
 *       ...
 *     }
 *   }
 * </pre>
 *
 * @author Brian Pontarelli
 */
public interface Plugin {
}
