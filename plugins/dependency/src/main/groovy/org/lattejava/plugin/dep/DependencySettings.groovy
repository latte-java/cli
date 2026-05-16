/*
 * Copyright (c) 2024-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.lattejava.dep.domain.License

/**
 * Settings for the Dependency plugin.
 *
 * @author Brian Pontarelli
 */
class DependencySettings {
  LicenseSettings license = new LicenseSettings()

  class LicenseSettings {
    /**
     * List of allowed license prefixes for the project. The default includes the most common licenses that are not
     * copyleft in any way.
     */
    List<String> allowedPrefixes = ["AFL-", "Apache-", "Artistic-", "BSD-", "ECL-", "EDL-", "EFL-", "EPL-", "LGPL-", "LPL-", "LPPL-", "MIT-", "MPL-", "OLDAP-", "PDDL-", "PHP-", "Python-", "SGI-", "UPL-", "W3C-", "ZPL-"]

    /**
     * List of allowed license ids (SPDX identifiers). The default includes the most common licenses that are now copyleft in
     * any way.
     */
    List<String> allowedIDs = ["GPL-2.0-with-classpath-exception", "ICU", "JSON", "MIT", "PostgreSQL", "Ruby", "Vim", "W3C", "X11", "Zlib"]

    /**
     * List of license objects that are allowed. This is a fall back for dependencies pulled from Maven that do not use SPDX.
     */
    List<License> allowedLicenses = []

    /**
     * List of artifact ids (wildcards are allowed) that are ignored when performing license analysis.
     */
    List<String> ignoredArtifactIDs = []
  }
}
