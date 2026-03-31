/*
 * Copyright (c) 2014-2024, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep.xml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.lattejava.dep.ArtifactTools;
import org.lattejava.BaseUnitTest;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.domain.Version;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * This class tests the artifact toolkit.
 *
 * @author Brian Pontarelli
 */
public class ArtifactToolsTest extends BaseUnitTest {
  @Test
  public void parse() throws Exception {
    Map<String, Version> mappings = new HashMap<>();
    mappings.put("org.example.test:badver:1.0.0.Final", new Version("1.0.0"));
    mappings.put("org.example.test:short-badver:1.0", new Version("1.0.0"));

    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaData(projectDir.resolve("src/test/resources/amd.xml"), mappings);
    assertEquals(amd.licenses, Arrays.asList(License.Licenses.get("ApacheV2_0"), License.parse("BSD_2_Clause", "Override the BSD license.")));
    assertEquals(amd.dependencies.groups.size(), 2);
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.size(), 2);
    assertEquals(amd.dependencies.groups.get("runtime").name, "runtime");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.project, "test-project");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.name, "test-project");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.project, "test-project2");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.name, "test-project2");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).version, new Version("2.0.0"));
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.type, "jar");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).exclusions, Arrays.asList(
        new ArtifactID("org.example", "exclude-1", "exclude-1", "jar"),
        new ArtifactID("org.example", "exclude-2", "exclude-2", "xml"),
        new ArtifactID("org.example", "exclude-3", "exclude-4", "zip")
    ));

    assertEquals(amd.dependencies.groups.get("compile").dependencies.size(), 4);
    assertEquals(amd.dependencies.groups.get("compile").name, "compile");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.project, "test-project3");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.name, "test-project3");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).version, new Version("3.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(0).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.project, "test-project4");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.name, "test-project4");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).version, new Version("4.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(1).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).id.project, "badver");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).id.name, "badver");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).nonSemanticVersion, "1.0.0.Final");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).id.project, "short-badver");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).id.name, "short-badver");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).nonSemanticVersion, "1.0");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).id.type, "jar");
  }

  @Test
  public void parseJSON() throws Exception {
    Map<String, Version> mappings = new HashMap<>();
    mappings.put("org.example.test:badver:1.0.0.Final", new Version("1.0.0"));
    mappings.put("org.example.test:short-badver:1.0", new Version("1.0.0"));

    ArtifactMetaData amd = ArtifactTools.parseArtifactMetaDataJSON(projectDir.resolve("src/test/resources/amd.json"), mappings);
    assertEquals(amd.licenses, Arrays.asList(License.Licenses.get("ApacheV2_0"), License.parse("BSD_2_Clause", "Override the BSD license.")));
    assertEquals(amd.dependencies.groups.size(), 2);
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.size(), 2);
    assertEquals(amd.dependencies.groups.get("runtime").name, "runtime");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.project, "test-project");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.name, "test-project");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(0).id.type, "jar");

    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.group, "org.example.test");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.project, "test-project2");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.name, "test-project2");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).version, new Version("2.0.0"));
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).id.type, "jar");
    assertEquals(amd.dependencies.groups.get("runtime").dependencies.get(1).exclusions, Arrays.asList(
        new ArtifactID("org.example", "exclude-1", "exclude-1", "jar"),
        new ArtifactID("org.example", "exclude-2", "exclude-2", "xml"),
        new ArtifactID("org.example", "exclude-3", "exclude-4", "zip")
    ));

    assertEquals(amd.dependencies.groups.get("compile").dependencies.size(), 4);
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).nonSemanticVersion, "1.0.0.Final");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(2).version, new Version("1.0.0"));
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).nonSemanticVersion, "1.0");
    assertEquals(amd.dependencies.groups.get("compile").dependencies.get(3).version, new Version("1.0.0"));
  }

  @Test
  public void json() throws Exception {
    Artifact d1 = new Artifact("group_name:project_name:name:1.0.0:type");
    Artifact d2 = new Artifact("group_name2:project_name2:name2:2.0.0:type2");
    Artifact d3 = new Artifact("group_name3:project_name3:name3:3.0.0:type3");
    Artifact d4 = new Artifact("group_name4:project_name4:name4:4.0.0:type4", null, false, Arrays.asList(
        new ArtifactID("org.example:exclude-1"),
        new ArtifactID("org.example:exclude-2:zip"),
        new ArtifactID("org.example:exclude-3:exclude-4:xml")
    ));
    Artifact d5 = new Artifact("badver:badver:badver:6.0.0:jar", "6.0.0.Final", false, Collections.emptyList());

    DependencyGroup compile = new DependencyGroup("compile", true);
    compile.dependencies.add(d1);
    compile.dependencies.add(d2);

    DependencyGroup runtime = new DependencyGroup("runtime", true);
    runtime.dependencies.add(d3);
    runtime.dependencies.add(d5);

    DependencyGroup compileOptional = new DependencyGroup("compile-optional", true);
    compileOptional.dependencies.add(d4);

    // Not exported — should be excluded from output
    DependencyGroup test = new DependencyGroup("test", false);
    test.dependencies.add(new Artifact("test:test:test:1.0.0:jar"));

    Dependencies deps = new Dependencies();
    deps.groups.put("compile", compile);
    deps.groups.put("runtime", runtime);
    deps.groups.put("compile-optional", compileOptional);
    deps.groups.put("test", test);

    ArtifactMetaData amd = new ArtifactMetaData(deps, License.Licenses.get("ApacheV2_0"), License.parse("BSD-1-Clause", "Override the license."));
    Path tmp = ArtifactTools.generateJSON(amd);
    assertNotNull(tmp);
    assertTrue(Files.isRegularFile(tmp));

    // Remove the non-exported group before comparing
    deps.groups.remove("test");

    // Round-trip: parse back and compare
    Map<String, Version> mappings = new HashMap<>();
    mappings.put("badver:badver:6.0.0.Final", new Version("6.0.0"));

    ArtifactMetaData amdOut = ArtifactTools.parseArtifactMetaDataJSON(tmp, mappings);
    assertEquals(amd, amdOut);

    Files.delete(tmp);
  }

  /**
   * Tests that the XML generation works correctly.
   */
  @Test
  public void xml() throws Exception {
    Artifact d1 = new Artifact("group_name\"quoted\":project_name:name\"quoted\":1.0.0:type");
    Artifact d2 = new Artifact("group_name2:project_name2\"quoted\":name2:2.0.0:type2");
    Artifact d3 = new Artifact("group_name3:project_name3:name3:3.0.0+\"quoted\":type3\"quoted\"");
    Artifact d4 = new Artifact("group_name4:project_name4:name4:4.0.0:type4", null, false, Arrays.asList(
        new ArtifactID("org.example:exclude-1"),
        new ArtifactID("org.example:exclude-2:zip"),
        new ArtifactID("org.example:exclude-3:exclude-4:xml")
    ));
    Artifact d5 = new Artifact("group_name5:project_name5:name5:5.0.0:type5", null, false, Arrays.asList(
        new ArtifactID("org.example:exclude-1"),
        new ArtifactID("org.example:exclude-2:zip"),
        new ArtifactID("org.example:exclude-3:exclude-4:xml")
    ));
    Artifact d6 = new Artifact("badver:badver:badver:6.0.0:jar", "6.0.0.Final", false, Collections.emptyList());

    DependencyGroup compile = new DependencyGroup("compile", true);
    compile.dependencies.add(d1);
    compile.dependencies.add(d2);

    DependencyGroup runtime = new DependencyGroup("runtime", true);
    runtime.dependencies.add(d3);
    runtime.dependencies.add(d6);

    DependencyGroup compileOptional = new DependencyGroup("compile-optional", true);
    compileOptional.dependencies.add(d4);

    // Not exported
    DependencyGroup test = new DependencyGroup("test", false);
    test.dependencies.add(d5);

    Dependencies deps = new Dependencies();
    deps.groups.put("compile", compile);
    deps.groups.put("runtime", runtime);
    deps.groups.put("compile-optional", compileOptional);
    deps.groups.put("test", test);

    ArtifactMetaData amd = new ArtifactMetaData(deps, License.Licenses.get("ApacheV2_0"), License.Licenses.get("BSD_2_Clause"), License.parse("BSD-1-Clause", "Override the license."));
    Path tmp = ArtifactTools.generateXML(amd);
    assertNotNull(tmp);
    assertTrue(Files.isRegularFile(tmp));

    String actual = new String(Files.readAllBytes(tmp));
    String expected = new String(Files.readAllBytes(Paths.get("../savant-dependency-management/src/test/resources/expected-amd.xml")));
    assertEquals(actual, expected);

    // Remove the non-exported group
    deps.groups.remove("test");

    // Then load and compare
    Map<String, Version> mappings = new HashMap<>();
    mappings.put("badver:badver:6.0.0.Final", new Version("6.0.0"));

    ArtifactMetaData amdOut = ArtifactTools.parseArtifactMetaData(tmp, mappings);
    assertEquals(amd, amdOut);

    Files.delete(tmp);
  }
}
