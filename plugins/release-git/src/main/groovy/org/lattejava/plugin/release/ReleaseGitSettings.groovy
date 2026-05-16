/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.release

/**
 * Settings class that defines the settings used by the release-git plugin.
 */
class ReleaseGitSettings {
  /**
   * The tag name to use for the release. Defaults to the project version. Use this to add a prefix or suffix to the
   * tag, for example {@code "v${project.version}"}.
   */
  String tag
}
