/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.deb

import org.lattejava.cli.domain.Project
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.output.Output

/**
 * Debian package plugin.
 *
 * @author Brian Pontarelli
 */
class DebianPlugin extends BaseGroovyPlugin {
  DebianPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
  }

  /**
   * Builds the Debian package using the nested fileSets and attributes.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   deb.build(to: "build/distributions", package: "example", section: "web") {
   *     version(upstream: "3.0.0", debian: "1")
   *     maintainer(name: "Inversoft", email: "sales@inversoft.com")
   *     description(synopsis: "This package rocks", extended: "No seriously it rocks really hard")
   *     tarFileSet(dir: "src/main/scripts")
   *   }
   * </pre>
   *
   * @param attributes The named attributes (to is required).
   * @param closure The closure that is invoked.
   * @return The number of files copied.
   */
  void build(Map<String, Object> attributes, @DelegatesTo(DebDelegate.class) Closure closure) {
    DebDelegate delegate = new DebDelegate(attributes, project)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()

    output.infoln("Building Debian package [${delegate.pkg}_${delegate.version}_${delegate.architecture}.deb]")
    delegate.build()
  }
}
