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
package org.lattejava.dep;

import org.lattejava.dep.domain.ReifiedArtifact;

/**
 * Thrown when an invalid license is encountered during the resolution process.
 *
 * @author Brian Pontarelli
 */
public class LicenseException extends RuntimeException {
  public final ReifiedArtifact artifact;

  public LicenseException(String id) {
    super("[" + id + "] is an invalid license or is missing the license text");
    this.artifact = null;
  }

  public LicenseException(ReifiedArtifact artifact) {
    super("The artifact [" + artifact + "] uses an invalid license " + artifact.licenses);
    this.artifact = artifact;
  }
}
