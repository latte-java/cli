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
package org.lattejava.dep.workflow.process;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.lattejava.BaseUnitTest;
import org.lattejava.dep.PathTools;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * This class tests the URLProcess class.
 *
 * @author Brian Pontarelli
 */
public class URLProcessTest extends BaseUnitTest {

  @Test(dataProvider = "fetchData")
  public void fetch(String url, String name, String version, String result) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.lattejava.test:" + name + ":" + name + ":" + version + ":jar", License.Licenses.get("ApacheV2_0"));
    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult fetchResult = ufp.fetch(item, new PublishWorkflow(new CacheProcess(output, cache.toString(), null, null)));
    assertNotNull(fetchResult);

    assertEquals((Object) fetchResult.file().toAbsolutePath(), Paths.get(result).toAbsolutePath());
  }

  @DataProvider(name = "fetchData")
  public Object[][] fetchData() {
    return new Object[][]{
        {makeLocalURL(), "multiple-versions", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar").toString()},
        {makeLocalURL(), "leaf1", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar").toString()},
        {"http://localhost:7042/test-deps/savant", "multiple-versions", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/multiple-versions/1.0.0/multiple-versions-1.0.0.jar").toString()},
        {"http://localhost:7042/test-deps/savant", "leaf1", "1.0.0", projectDir.resolve("build/test/cache/org/savantbuild/test/leaf1/1.0.0/leaf1-1.0.0.jar").toString()}
    };
  }

  @Test(dataProvider = "urls")
  public void metaData(String url) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.lattejava.test:multiple-versions:multiple-versions:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactMetaDataFile());
    FetchResult amd = ufp.fetch(item, pw);
    assertNotNull(amd);
  }

  @Test(dataProvider = "urls")
  public void missingAMD(String url) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.lattejava.test:missing-item:missing-item:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactMetaDataFile());
    FetchResult result = ufp.fetch(item, pw);
    assertNull(result);
  }

  @Test(dataProvider = "urls")
  public void missingItem(String url) throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.lattejava.test:missing-item:missing-item:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, url, null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult result = ufp.fetch(item, pw);
    assertNull(result);
  }

  @Test
  public void missingMD5() throws Exception {
    PathTools.prune(projectDir.resolve("build/test/cache"));

    Artifact artifact = new ReifiedArtifact("org.lattejava.test:missing-md5:missing-md5:1.0.0:jar", License.Licenses.get("ApacheV2_0"));

    CacheProcess process = new CacheProcess(output, cache.toString(), null, null);
    PublishWorkflow pw = new PublishWorkflow();
    pw.getProcesses().add(process);

    URLProcess ufp = new URLProcess(output, makeLocalURL(), null, null);
    ResolvableItem item = new ResolvableItem(artifact.id.group, artifact.id.project, artifact.id.name, artifact.version.toString(), artifact.getArtifactFile());
    FetchResult result = ufp.fetch(item, pw);
    assertNull(result);
  }


  @DataProvider(name = "urls")
  public Object[][] urls() {
    return new Object[][]{
        {makeLocalURL()},
        {"http://localhost:7042/test-deps/savant"}
    };
  }

  private String makeLocalURL() {
    return projectDir.toAbsolutePath().toUri() + "/test-deps/savant";
  }
}
