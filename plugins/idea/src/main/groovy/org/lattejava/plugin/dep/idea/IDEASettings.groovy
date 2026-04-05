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
package org.lattejava.plugin.dep.idea

import java.nio.file.Paths

import org.lattejava.cli.domain.Project

/**
 * Settings for the IDEA plugin.
 *
 * @author Brian Pontarelli
 */
class IDEASettings {
  /**
   * The IML file. This can be defined as a String or a Path object like this:
   *
   * <pre>
   *   idea.settings.imlFile = "foo.iml"
   *
   *   or
   *
   *   idea.settings.imlFile = Paths.get("foo.iml")
   * </pre>
   */
  Object imlFile

  /**
   * Dependencies map. This maps IDEA scopes to Dependency sets. These are used to update the .iml file
   * based on the project's dependencies. The default configuration is:
   *
   * <pre>
   *   [
   *     "PROVIDED": [
   *       [group: "provided", transitive: true, fetchSource: true, transitiveGroups: ["provided", "compile", "runtime"]]
   *     ],
   *     "COMPILE": [
   *       [group: "compile", transitive: false, fetchSource: true]
   *     ],
   *     "TEST": [
   *       [group: "test-compile", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]],
   *       [group: "test-runtime", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]]
   *     ],
   *     "RUNTIME": [
   *       [group: "compile", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]],
   *       [group: "runtime", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]]
   *     ]
   * ]
   * </pre>
   */
  Map<String, List<Map<String, Object>>> dependenciesMap = [
      "PROVIDED": [
          [group: "provided", transitive: true, fetchSource: true, transitiveGroups: ["provided", "compile", "runtime"]]
      ],
      "COMPILE": [
          [group: "compile", transitive: false, fetchSource: true]
      ],
      "TEST": [
          [group: "test-compile", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]],
          [group: "test-runtime", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]]
      ],
      "RUNTIME": [
          [group: "compile", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]],
          [group: "runtime", transitive: true, fetchSource: true, transitiveGroups: ["compile", "runtime"]]
      ]
  ]

  /**
   * Module map. This maps from Savant dependencies in the project build file to modules in the IDEA project. For
   * example, let's say one of your modules depends on another module (foo depends on bar). In your Savant build file
   * for the foo project will have this dependency:
   *
   * <pre>
   *   dependencies {
   *     group("compile") {
   *       dependency(id: "org.example:bar:1.0.0-{integration}")
   *     }
   *   }
   * </pre>
   *
   * Inside your IntelliJ IDEA project, the bar project might be included as a module named "bar-module". Therefore,
   * you will setup this moduleMap:
   *
   * <pre>
   *   idea.settings.moduleMap = ["org.example:bar:1.0.0-{integration}":"bar-module"]
   * </pre>
   */
  Map<String, String> moduleMap = [:]

  /**
   * Constructs the default IDEA plugin settings. This sets the {@link #imlFile} field to the project name plus the
   * {@code .iml} extension.
   *
   * @param project The project.
   */
  IDEASettings(Project project) {
    this.imlFile = Paths.get(project.name + ".iml")
  }
}
