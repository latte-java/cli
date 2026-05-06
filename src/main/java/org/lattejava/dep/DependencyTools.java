/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep;

import org.lattejava.dep.domain.ArtifactID;

/**
 * Helpers for dependency objects.
 *
 * @author Brian Pontarelli
 */
public final class DependencyTools {
  /**
   * Determines if the given artifact ID matches the given exclusion, taking into consideration wildcards in the
   * exclusion.
   *
   * @param artifact  The artifact ID.
   * @param exclusion The exclusion.
   * @return True if it matches, false if not.
   */
  public static boolean matchesExclusion(ArtifactID artifact, ArtifactID exclusion) {
    if (artifact.equals(exclusion)) {
      return true;
    }

    boolean groupMatches = exclusion.group.equals("*") || artifact.group.equals(exclusion.group);
    boolean nameMatches = exclusion.name.equals("*") || artifact.name.equals(exclusion.name);
    boolean projectMatches = exclusion.project.equals("*") || artifact.project.equals(exclusion.project);
    boolean typeMatches = exclusion.type.equals("*") || artifact.type.equals(exclusion.type);
    return groupMatches && nameMatches && projectMatches && typeMatches;
  }
}
