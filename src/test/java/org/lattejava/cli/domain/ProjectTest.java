/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.domain;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.domain.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests the project domain.
 *
 * @author Brian Pontarelli
 */
public class ProjectTest extends BaseUnitTest {
  @Test
  public void toArtifact() {
    Project project = new Project(projectDir, output);
    project.group = "group";
    project.name = "name";
    project.version = new Version("1.1.1");
    project.licenses.add(License.parse("BSD_2_Clause", null));
    assertEquals(project.toArtifact(), new ReifiedArtifact("group:name:name:1.1.1:jar", License.parse("BSD_2_Clause", null)));
  }
}
