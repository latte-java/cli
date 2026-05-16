/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.linter

import java.nio.file.Path
import java.nio.file.Paths

/**
 * A settings object that defines options for how to execute PMD.
 *
 * @author Daniel DeGroff
 */
class LinterSettings {
  Path reportDirectory = Paths.get("build/linter-reports")
}
