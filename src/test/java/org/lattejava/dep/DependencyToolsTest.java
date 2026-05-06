/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
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
