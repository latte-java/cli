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
