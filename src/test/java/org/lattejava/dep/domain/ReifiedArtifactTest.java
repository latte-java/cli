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

import org.lattejava.BaseUnitTest;
import org.lattejava.domain.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

/**
 * Tests the artifact domain object.
 *
 * @author Brian Pontarelli
 */
public class ReifiedArtifactTest extends BaseUnitTest {
  @Test
  public void construct() {
    assertEquals(new ReifiedArtifact("group:name:2.0.0", License.Licenses.get("Apache-1.0")), new ReifiedArtifact(new ArtifactID("group", "name", "name", "jar"), new Version("2.0.0"), License.Licenses.get("Apache-1.0")));
    assertEquals(new ReifiedArtifact("group:name:2.0.0:zip", License.Licenses.get("Apache-2.0")), new ReifiedArtifact(new ArtifactID("group", "name", "name", "zip"), new Version("2.0.0"), License.Licenses.get("Apache-2.0")));
    assertEquals(new ReifiedArtifact("group:project:name:2.0.0:zip", License.parse("Commercial", "License")), new ReifiedArtifact(new ArtifactID("group", "project", "name", "zip"), new Version("2.0.0"), License.parse("Commercial", "License")));
    assertNotEquals(new ReifiedArtifact("group:project:name:1.0.0:zip", License.Licenses.get("Apache-1.0")), new ReifiedArtifact(new ArtifactID("group", "project", "name", "zip"), new Version("1.0.0"), License.parse("Commercial", "License")));
  }

  @Test
  public void syntheticMethods() {
    assertEquals(new ReifiedArtifact("group:name:2.0.0", License.Licenses.get("Apache-2.0")).getArtifactFile(), "name-2.0.0.jar");
    assertEquals(new ReifiedArtifact("group:name:2.0.0", License.Licenses.get("Apache-2.0")).getArtifactMetaDataFile(), "name-2.0.0.jar.amd");
    assertEquals(new ReifiedArtifact("group:name:2.0.0", License.Licenses.get("Apache-2.0")).getArtifactSourceFile(), "name-2.0.0-src.jar");
  }
}
