/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep.maven

/**
 * A settings object that defines options for how Latte project files are converted to POMs.
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
