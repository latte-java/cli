/*
 * Copyright (c) 2022-2025, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep.maven;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.domain.Version;
import org.lattejava.output.SystemOutOutput;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

/**
 * Tests the Maven malarky.
 *
 * @author Brian Pontarelli
 */
public class MavenToolsTest extends BaseUnitTest {
  private static POM parseGroovyJsonPOM() {
    POM pom = MavenTools.parsePOM(Paths.get("../savant-dependency-management/src/test/resources/groovy-json-4.0.6.pom"), new SystemOutOutput(true));
    assertEquals(pom.group, "org.apache.groovy");
    assertEquals(pom.id, "groovy-json");
    assertEquals(pom.name, "Apache Groovy");
    assertEquals(pom.version, "4.0.6");
    assertNull(pom.parentGroup);
    assertNull(pom.parentId);
    assertNull(pom.parentVersion);
    assertEquals(pom.dependencies, Arrays.asList(
            new MavenDependency("org.apache.groovy", "groovy", "4.0.6", "compile")
        )
    );
    assertEquals(pom.licenses, Collections.singletonList(new MavenLicense("repo", "The Apache Software License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0.txt")));
    return pom;
  }

  @Test
  public void parse() {
    POM pom = MavenTools.parsePOM(Paths.get("../savant-dependency-management/src/test/resources/groovy-4.0.5.pom"), new SystemOutOutput(true));
    assertEquals(pom.group, "org.apache.groovy");
    assertEquals(pom.id, "groovy");
    assertEquals(pom.name, "Apache Groovy");
    assertEquals(pom.version, "4.0.5");
    assertNull(pom.parentGroup);
    assertNull(pom.parentId);
    assertNull(pom.parentVersion);
    assertEquals(pom.dependencies, Arrays.asList(
            new MavenDependency("org.codehaus.gpars", "gpars", "1.2.1", "compile", true, Collections.singletonList(new MavenExclusion("org.codehaus.groovy", "groovy-all"))),
            new MavenDependency("org.apache.ivy", "ivy", "2.5.0", "compile", true, Collections.singletonList(new MavenExclusion("*", "*"))),
            new MavenDependency("com.thoughtworks.xstream", "xstream", "1.4.19", "compile", true,
                Arrays.asList(new MavenExclusion("junit", "junit"), new MavenExclusion("xpp3", "xpp3_min"), new MavenExclusion("xmlpull", "xmlpull"), new MavenExclusion("jmock", "jmock"))
            ),
            new MavenDependency("org.fusesource.jansi", "jansi", "2.4.0", "compile", true, Collections.emptyList())
        )
    );
    assertEquals(pom.licenses, Collections.singletonList(new MavenLicense("repo", "The Apache Software License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0.txt")));

    // Everything is optional
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, Collections.emptyMap());
    assertEquals(dependencies, new Dependencies());
  }

  @Test
  public void parseRequired() {
    // arrange
    POM pom = parseGroovyJsonPOM();

    // act
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, Collections.emptyMap());

    // assert
    var expectedArtifact = new Artifact("org.apache.groovy:groovy:4.0.6");
    var expectedDependencyGroup = new DependencyGroup("compile", true, expectedArtifact);
    assertEquals(dependencies, new Dependencies(expectedDependencyGroup));
  }

  @Test
  public void parseVersionRemappedMavenCausesVersionException() {
    // arrange
    POM pom = parseGroovyJsonPOM();
    pom.dependencies.get(0).version = "4.0.heydude";
    var mappings = Map.of("org.apache.groovy:groovy:4.0.heydude", new Version("4.0.6"));

    // act
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, mappings);

    // assert
    var expectedArtifact = new Artifact("org.apache.groovy:groovy:4.0.6");
    var expectedDependencyGroup = new DependencyGroup("compile", true, expectedArtifact);
    assertEquals(dependencies, new Dependencies(expectedDependencyGroup));
  }

  @Test
  public void parseVersionRemappedMavenNotCausesException() {
    // arrange
    POM pom = parseGroovyJsonPOM();
    pom.dependencies.get(0).version = "4.0";
    var mappings = Map.of("org.apache.groovy:groovy:4.0", new Version("4.0.6"));

    // act
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, mappings);

    // assert
    var expectedArtifact = new Artifact("org.apache.groovy:groovy:4.0.6");
    var expectedDependencyGroup = new DependencyGroup("compile", true, expectedArtifact);
    assertEquals(dependencies, new Dependencies(expectedDependencyGroup));
  }

  @Test
  public void parse_concreteRange() {
    POM pom = new POM();
    pom.version = "1.80.0";
    // No mapping required for a concrete range
    pom.dependencies.add(new MavenDependency("org.bouncycastle", "bcprov-jdk18on", "[1.80]", "compile", "jar"));

    pom.replaceKnownVariablesAndFillInDependencies();
    pom.replaceRangeValuesWithMappings(Map.of());

    // Expect the maven dependency to have a concrete version
    assertEquals(pom.dependencies, Arrays.asList(
            new MavenDependency("org.bouncycastle", "bcprov-jdk18on", "1.80", "compile", "jar")
        )
    );

    // The final dependency has a sematic version of 1.80.0
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, Collections.emptyMap());
    assertEquals(dependencies, new Dependencies(new DependencyGroup("compile", true, new Artifact("org.bouncycastle:bcprov-jdk18on:1.80.0"))));
  }

  @Test
  public void parse_versionRanges() {
    POM pom = MavenTools.parsePOM(Paths.get("../savant-dependency-management/src/test/resources/bcutil-jdk18on-1.80.pom"), new SystemOutOutput(true));
    pom.replaceKnownVariablesAndFillInDependencies();
    pom.replaceRangeValuesWithMappings(Map.of("org.bouncycastle:bcprov-jdk18on:[1.80,1.81)", "1.80"));

    assertEquals(pom.group, "org.bouncycastle");
    assertEquals(pom.id, "bcutil-jdk18on");
    assertEquals(pom.name, "Bouncy Castle ASN.1 Extension and Utility APIs");
    assertEquals(pom.version, "1.80");
    assertNull(pom.parentGroup);
    assertNull(pom.parentId);
    assertNull(pom.parentVersion);

    // Expect the maven dependency to have a concrete version
    assertEquals(pom.dependencies, Arrays.asList(
            new MavenDependency("org.bouncycastle", "bcprov-jdk18on", "1.80", "compile", "jar")
        )
    );

    // The final dependency has a sematic version of 1.80.0
    Dependencies dependencies = MavenTools.toSavantDependencies(pom, Collections.emptyMap());
    assertEquals(dependencies, new Dependencies(new DependencyGroup("compile", true, new Artifact("org.bouncycastle:bcprov-jdk18on:1.80.0"))));
  }

  @Test
  public void parse_versionRanges_noMapping() {
    POM pom = MavenTools.parsePOM(Paths.get("../savant-dependency-management/src/test/resources/bcutil-jdk18on-1.80.pom"), new SystemOutOutput(true));
    pom.replaceKnownVariablesAndFillInDependencies();

    try {
      pom.replaceRangeValuesWithMappings(Map.of());
      fail("Expected this to fail.");
    } catch (VersionRangeException e) {

      assertEquals(e.getMessage(), """
          Fortunately, version ranges are not supported. You must provide a rangeMapping in the build file.
          
          Example:
          
          semanticVersions {
            rangeMapping(id: "org.bouncycastle:bcprov-jdk18on:[1.80,1.81)", version: "{version}")
          }
          
          Where {version} is the concrete version within the range that you wish to utilize from the external maven repository.
          Note that this version should be the exact version as it is found in Maven. If the version is not semantic, it is possible
          that you may also need to provide a version mapping as well.
          """);
    }
  }
}
