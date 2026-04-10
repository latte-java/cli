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
package org.lattejava.plugin.release

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.lattejava.dep.domain.Artifact
import org.lattejava.dep.domain.ArtifactMetaData
import org.lattejava.dep.domain.Dependencies
import org.lattejava.dep.domain.DependencyGroup
import org.lattejava.dep.domain.License
import org.lattejava.dep.domain.Publication
import org.lattejava.dep.domain.ReifiedArtifact
import org.lattejava.dep.workflow.FetchWorkflow
import org.lattejava.dep.workflow.PublishWorkflow
import org.lattejava.dep.workflow.Workflow
import org.lattejava.dep.workflow.process.CacheProcess
import org.lattejava.cli.domain.Project
import org.lattejava.domain.Version
import org.json.simple.parser.JSONParser
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.lattejava.cli.runtime.RuntimeFailureException
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertNotNull
import static org.testng.Assert.assertTrue
import static org.testng.Assert.fail

/**
 * Tests the ReleaseGitPlugin class.
 *
 * @author Brian Pontarelli
 */
class ReleaseGitPluginTest {
  public static Path projectDir

  Path gitDir

  Path gitRemoteDir

  Output output

  Project project

  Path publishDir

  Path mainPub

  Path mainPubSource

  Path testPub

  Path testPubSource

  Path cacheDir

  Path integrationDir

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("build.savant"))) {
      projectDir = Paths.get("../release-git")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir.resolve("build/test/release/git-repo"), output)
    project.group = "org.lattejava.test"
    project.name = "release-git-test"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    cacheDir = projectDir.resolve("../../test-deps/latte")
    integrationDir = projectDir.resolve("../../test-deps/integration")

    project.workflow = new Workflow(
        new FetchWorkflow(output, new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), null)),
        new PublishWorkflow(new CacheProcess(output, cacheDir.toString(), integrationDir.toString(), null)),
        output
    )

    FileTools.prune(projectDir.resolve("build/test/release"))
    Files.createDirectories(projectDir.resolve("build/test/release/git-remote-repo"))
    Files.createDirectories(projectDir.resolve("build/test/release/git-repo"))

    // Create the git remote repository
    gitRemoteDir = projectDir.resolve("build/test/release/git-remote-repo").toRealPath()
    assertEquals("git init --bare ${gitRemoteDir}".execute().waitFor(), 0)

    // Create a second git repository (the project) and make the first repository a remote
    gitDir = projectDir.resolve("build/test/release/git-repo").toRealPath()
    assertEquals("git init -b main ${gitDir}".execute().waitFor(), 0)
    assertEquals("git remote add origin ${gitRemoteDir.toUri()}".execute([], gitDir.toFile()).waitFor(), 0)

    // Create a temp dir for publishing
    publishDir = projectDir.resolve("build/test/release/publish")
    project.publishWorkflow = new PublishWorkflow(new CacheProcess(output, publishDir.toString(), null, null))

    // Create the publications and the files
    mainPub = gitDir.resolve("main-pub.txt")
    Files.write(mainPub, "Main Pub".getBytes())
    mainPubSource = gitDir.resolve("main-pub-source.txt")
    Files.write(mainPubSource, "Main Pub Source".getBytes())
    testPub = gitDir.resolve("test-pub.txt")
    Files.write(testPub, "Test Pub".getBytes())
    testPubSource = gitDir.resolve("test-pub-source.txt")
    Files.write(testPubSource, "Test Pub Source".getBytes())
    assertEquals("git add main-pub.txt".execute([], gitDir.toFile()).waitFor(), 0)
    assertEquals("git add main-pub-source.txt".execute([], gitDir.toFile()).waitFor(), 0)
    assertEquals("git add test-pub.txt".execute([], gitDir.toFile()).waitFor(), 0)
    assertEquals("git add test-pub-source.txt".execute([], gitDir.toFile()).waitFor(), 0)
    assertEquals("git commit -am Test".execute([], gitDir.toFile()).waitFor(), 0)
    assertEquals("git push -u origin main".execute([], gitDir.toFile()).waitFor(), 0)
  }

  @Test
  void releaseCanNotPull() throws Exception {
    project.dependencies = null
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Remove the git remote
    assertEquals("git remote remove origin".execute([], gitDir.toFile()).waitFor(), 0)

    // Run the release
    try {
      ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
      plugin.release()
      fail("Should have failed")
    } catch (e) {
      // Expected
      assertTrue(e.message.contains("Unable to pull from remote Git repository"))
    }

    assertReleaseDidNotRun()
  }

  @Test
  void releaseWithDependencyIntegrationBuild() throws Exception {
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:intermediate:1.0.0")
        ),
        new DependencyGroup("test", false,
            new Artifact("org.lattejava.test:leaf1:1.0.0")
        )
    )
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Run the release
    ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.release()
      fail("Should have failed")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("integration release"))
    }
  }

  @Test
  void releaseWithDependencies() throws Exception {
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", true,
            new Artifact("org.lattejava.test:leaf2:1.0.0")
        ),
        new DependencyGroup("test", false,
            new Artifact("org.lattejava.test:leaf1:1.0.0")
        )
    )
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Run the release
    ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
    plugin.release()

    assertNotNull(project.artifactGraph)

    assertTagsExist()

    // Verify the SubVersion publish and the AMD files
    assertStatus(true)
    def parser = new JSONParser()
    assertEquals(parser.parse(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar.amd")))),
        parser.parse('{"licenses":[{"type":"Commercial","text":"License"}],"dependencyGroups":{"compile":[{"id":"org.lattejava.test:leaf2:leaf2:1.0.0:jar"}]}}'))
    assertEquals(parser.parse(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar.amd")))),
        parser.parse('{"licenses":[{"type":"Commercial","text":"License"}],"dependencyGroups":{"compile":[{"id":"org.lattejava.test:leaf2:leaf2:1.0.0:jar"}]}}'))
  }

  @Test
  void releaseWithoutDependencies() throws Exception {
    project.dependencies = null
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Run the release
    ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
    plugin.release()

    assertTagsExist()

    // Verify the SubVersion publish and the AMD files
    assertStatus(true)
    def parser = new JSONParser()
    assertEquals(parser.parse(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar.amd")))),
        parser.parse('{"licenses":[{"type":"Commercial","text":"License"}]}'))
    assertEquals(parser.parse(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar.amd")))),
        parser.parse('{"licenses":[{"type":"Commercial","text":"License"}]}'))
  }

  @Test
  void releaseWithCustomTag() throws Exception {
    project.dependencies = null
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Run the release with a custom tag
    ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.tag = "v-foo-bar"
    plugin.release()

    // Verify the custom tag exists (not the version-only tag)
    String tags = "git tag -l".execute([], gitDir.toFile()).text
    assertTrue(tags.contains("v-foo-bar"))
    assertFalse(tags.contains("1.0.0"))

    tags = "git tag -l".execute([], gitRemoteDir.toFile()).text
    assertTrue(tags.contains("v-foo-bar"))
    assertFalse(tags.contains("1.0.0"))

    assertStatus(true)
  }

  @Test
  void releaseDefaultTag() throws Exception {
    project.dependencies = null
    setupPublications(project, mainPub, mainPubSource, testPub, testPubSource)

    // Run the release without changing the tag — should default to the version
    ReleaseGitPlugin plugin = new ReleaseGitPlugin(project, new RuntimeConfiguration(), output)
    assertEquals(plugin.settings.tag, "1.0.0")
    plugin.release()

    assertTagsExist()
    assertStatus(true)
  }

  private static void setupPublications(Project project, Path mainPub, Path mainPubSource, Path testPub, Path testPubSource) {
    Publication mainPublication = new Publication(
        new ReifiedArtifact("org.lattejava.test:release-git-test:release-git-main:1.0.0:jar", [License.parse("Commercial", "License")]),
        new ArtifactMetaData(project.dependencies, [License.parse("Commercial", "License")]),
        mainPub,
        mainPubSource
    )
    Publication testPublication = new Publication(
        new ReifiedArtifact("org.lattejava.test:release-git-test:release-git-test:1.0.0:jar", [License.parse("Commercial", "License")]),
        new ArtifactMetaData(project.dependencies, [License.parse("Commercial", "License")]),
        testPub,
        testPubSource
    )
    project.publications.publicationGroups.put("main", [mainPublication])
    project.publications.publicationGroups.put("test", [testPublication])
  }

  private void assertTagsExist() {
    // Verify the tag exists
    String output = "git tag -l".execute([], gitDir.toFile()).text
    assertTrue(output.contains("1.0.0"))

    // Verify the tag is pushed
    output = "git tag -l".execute([], gitRemoteDir.toFile()).text
    assertTrue(output.contains("1.0.0"))
  }

  private void assertStatus(boolean published) {
    // Verify the publications are published or not
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar.sha256")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar.amd")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar.amd.sha256")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0-src.jar")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0-src.jar.sha256")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar.sha256")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar.amd")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar.amd.sha256")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0-src.jar")), published)
    assertEquals(Files.isRegularFile(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0-src.jar.sha256")), published)

    // Check out the files from SVN and verify their contents
    if (published) {
      assertEquals(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0.jar"))), "Main Pub")
      assertEquals(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-main-1.0.0-src.jar"))), "Main Pub Source")
      assertEquals(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0.jar"))), "Test Pub")
      assertEquals(new String(Files.readAllBytes(publishDir.resolve("org/lattejava/test/release-git-test/1.0.0/release-git-test-1.0.0-src.jar"))), "Test Pub Source")
    }
  }

  private void assertReleaseDidNotRun() {
    // Verify the tags don't exist
    String output = "git tag -l".execute([], gitDir.toFile()).text
    assertFalse(output.contains("1.0.0"))

    // Verify the tag is pushed
    output = "git tag -l".execute([], gitRemoteDir.toFile()).text
    assertFalse(output.contains("1.0.0"))

    // Ensure nothing was published
    assertStatus(false)
  }
}
