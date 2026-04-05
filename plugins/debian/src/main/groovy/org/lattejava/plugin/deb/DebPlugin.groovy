/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.plugin.deb

import org.lattejava.cli.domain.Project
import org.lattejava.output.Output
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration

/**
 * Debian package plugin.
 *
 * @author Brian Pontarelli
 */
class DebPlugin extends BaseGroovyPlugin {

  DebPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
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
