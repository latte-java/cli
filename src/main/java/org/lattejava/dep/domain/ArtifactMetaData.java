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
package org.lattejava.dep.domain;

import java.util.Arrays;
import java.util.List;

/**
 * This class is the model for the artifact metadata XML file that is published along with artifacts.
 *
 * @author Brian Pontarelli
 */
public class ArtifactMetaData {
  public final Dependencies dependencies;

  public final List<License> licenses;

  public ArtifactMetaData(Dependencies dependencies, License... licenses) {
    this(dependencies, Arrays.asList(licenses));
  }

  public ArtifactMetaData(Dependencies dependencies, List<License> licenses) {
    this.dependencies = dependencies;
    this.licenses = licenses;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ArtifactMetaData that = (ArtifactMetaData) o;
    return dependencies.equals(that.dependencies) && licenses.equals(that.licenses);
  }

  @Override
  public int hashCode() {
    int result = dependencies.hashCode();
    result = 31 * result + licenses.hashCode();
    return result;
  }
}
