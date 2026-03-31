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
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the dependency tools.
 *
 * @author Brian Pontarelli
 */
public class DependencyToolsTest {
  @Test
  public void matchesExclusion() {
    assertFalse(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("bar:baz")));

    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("foo:bar:bar:jar")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("foo:bar")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("*:bar:bar:jar")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("*:*:bar:jar")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("*:*:*:jar")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("*:*:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("*:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("foo:*:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("foo:bar:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar"), new ArtifactID("foo:bar:bar:*")));

    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("foo:bar:bar:xml")));
    assertFalse(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("foo:bar")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("*:bar:bar:xml")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("*:*:bar:xml")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("*:*:*:xml")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("*:*:*:*")));
    assertFalse(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("*:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("foo:*:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("foo:bar:*:*")));
    assertTrue(DependencyTools.matchesExclusion(new ArtifactID("foo:bar:xml"), new ArtifactID("foo:bar:bar:*")));
  }
}
