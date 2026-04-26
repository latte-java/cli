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
