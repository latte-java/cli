/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.dep.maven

/**
 * A settings object that defines options for how Savant project files are converted to POMs.
 *
 * @author Brian Pontarelli
 */
class POMSettings {
  /**
   * Dependency groups to scope/optional mapping. The default configuration is:
   *
   * <pre>
   *   [
   *     "compile": ["scope": "compile", "optional": false],
   *     "compile-optional": ["scope": "compile", "optional": true],
   *     "provided": ["scope": "provided", "optional": false],
   *     "runtime": ["scope": "runtime", "optional": false],
   *     "test-compile": ["scope": "test", "optional": false],
   *     "test-runtime": ["scope": "test", "optional": false]
   * ]
   * </pre>
   */
  Map<String, List<Map<String, Object>>> groupToScope = [
      "compile": ["scope": "compile", "optional": false],
      "compile-optional": ["scope": "compile", "optional": true],
      "provided": ["scope": "provided", "optional": false],
      "runtime": ["scope": "runtime", "optional": false],
      "test-compile": ["scope": "test", "optional": false],
      "test-runtime": ["scope": "test", "optional": false]
  ]
}
