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
package org.lattejava.plugin.deb

import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.dep.domain.License
import org.lattejava.io.FileTools
import org.lattejava.io.tar.TarTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.testng.Assert.assertEquals

/**
 * Tests the FilePlugin class.
 *
 * @author Brian Pontarelli
 */
class DebianPluginTest {
  public static Path projectDir

  Output output

  Project project

  DebianPlugin plugin

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("project.latte"))) {
      projectDir = Paths.get("../deb")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir, output)
    project.group = "org.lattejava.test"
    project.name = "dev-test"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    plugin = new DebianPlugin(project, new RuntimeConfiguration(), output)
  }

  @Test
  void build() {
    FileTools.prune(projectDir.resolve("build/test"))

    plugin.build(to: "build/test", package: "test", architecture: "x86", conflicts: "nothing", depends: "test2", enhances: "test3",
        homepage: "http://www.inversoft.com", preDepends: "test4", priority: "required", provides: "test5", recommends: "test6",
        replaces: "test7", section: "web", suggests: "test8", preInst: "src/test/scripts/preinst", preRm: "src/test/scripts/prerm",
        postInst: "src/test/scripts/postinst", postRm: "src/test/scripts/postrm") {
      version(upstream: "3.0.0-M4", debian: "1")
      description(synopsis: "Description synopsis", extended: """Description extended
line2
line3""")
      maintainer(name: "Inversoft", email: "sales@inversoft.com")
      tarFileSet(dir: "src/main/groovy", prefix: "main-groovy-files")
      tarFileSet(dir: "src/test/groovy", prefix: "test-groovy-files")
      confFileSet(dir: "src/test/conf", prefix: "etc/init.d")
      directory(name: "directory/nested", mode: 0x755)
    }

    // Unpack everything
    ArArchiveInputStream aais = new ArArchiveInputStream(Files.newInputStream(projectDir.resolve("build/test/test_3.0.0-M4-1_x86.deb")))
    ArArchiveEntry entry = aais.getNextArEntry()
    Path debianBinary = projectDir.resolve("build/test/debian-binary")
    assertEquals(entry.getName(), "debian-binary")
    Files.copy(aais, debianBinary)
    assertEquals(new String(Files.readAllBytes(debianBinary)), "2.0\n")

    entry = aais.getNextArEntry()
    Path controlTar = projectDir.resolve("build/test/control.tar.gz")
    assertEquals(entry.getName(), "control.tar.gz")
    Files.copy(aais, controlTar)

    Path controlExplodeDir = projectDir.resolve("build/test/control")
    TarTools.untar(controlTar, controlExplodeDir, false, false)
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("conffiles"))), new String(Files.readAllBytes(projectDir.resolve("src/test/expected/conffiles"))))
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("md5sums"))), new String(Files.readAllBytes(projectDir.resolve("src/test/expected/md5sums"))))
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("control"))), new String(Files.readAllBytes(projectDir.resolve("src/test/expected/control"))))
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("postinst"))), new String(Files.readAllBytes(projectDir.resolve("src/test/scripts/postinst"))))
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("postrm"))), new String(Files.readAllBytes(projectDir.resolve("src/test/scripts/postrm"))))
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("preinst"))), new String(Files.readAllBytes(projectDir.resolve("src/test/scripts/preinst"))))
    assertEquals(new String(Files.readAllBytes(controlExplodeDir.resolve("prerm"))), new String(Files.readAllBytes(projectDir.resolve("src/test/scripts/prerm"))))

    Path dataTar = projectDir.resolve("build/test/data.tar.gz")
    entry = aais.getNextArEntry()
    assertEquals(entry.getName(), "data.tar.gz")
    Files.copy(aais, dataTar)

    Path dataExplodeDir = projectDir.resolve("build/test/data")
    TarTools.untar(dataTar, dataExplodeDir, false, false)
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/ChangeLog.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/ChangeLog.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/DebDelegate.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/DebDelegate.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/DebianPlugin.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/DebianPlugin.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/Description.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/Description.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/Maintainer.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/Maintainer.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/Priority.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/Priority.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/Section.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/Section.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("main-groovy-files/org/lattejava/plugin/deb/Version.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/main/groovy/org/lattejava/plugin/deb/Version.groovy"))))
    assertEquals(new String(Files.readAllBytes(dataExplodeDir.resolve("test-groovy-files/org/lattejava/plugin/deb/DebianPluginTest.groovy"))),
        new String(Files.readAllBytes(projectDir.resolve("src/test/groovy/org/lattejava/plugin/deb/DebianPluginTest.groovy"))))
  }
}
