/*
 * Copyright (c) 2013-2025, Inversoft Inc., All Rights Reserved
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
package org.lattejava.cli.parser.groovy;

import java.util.List;
import java.util.Map;

import org.lattejava.dep.workflow.FetchWorkflow;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.dep.workflow.Workflow;
import org.lattejava.dep.workflow.process.CacheProcess;
import org.lattejava.dep.workflow.process.MavenProcess;
import org.lattejava.dep.workflow.process.Process;
import org.lattejava.dep.workflow.process.S3Process;
import org.lattejava.dep.workflow.process.URLProcess;
import org.lattejava.domain.Version;
import org.lattejava.output.Output;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.util.LattePaths;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Groovy delegate that captures the Workflow configuration from the project file. The methods of this class capture the
 * configuration from the DSL.
 *
 * @author Brian Pontarelli
 */
public class WorkflowDelegate {
  public static final String defaultMavenDir = System.getProperty("user.home") + "/.m2/repository";

  public final Output output;

  public final Workflow workflow;

  public WorkflowDelegate(Output output, Workflow workflow) {
    this.output = output;
    this.workflow = workflow;
  }

  /**
   * Configures the fetch workflow processes.
   *
   * @param closure The closure. This closure uses the delegate class {@link ProcessDelegate}.
   */
  public void fetch(@DelegatesTo(ProcessDelegate.class) Closure<?> closure) {
    closure.setDelegate(new ProcessDelegate(output, workflow.fetchWorkflow.processes));
    closure.setResolveStrategy(Closure.DELEGATE_FIRST);
    closure.run();
  }

  /**
   * Configures the publish workflow processes.
   *
   * @param closure The closure. This closure uses the delegate class {@link ProcessDelegate}.
   */
  public void publish(@DelegatesTo(ProcessDelegate.class) Closure<?> closure) {
    closure.setDelegate(new ProcessDelegate(output, workflow.publishWorkflow.processes));
    closure.setResolveStrategy(Closure.DELEGATE_FIRST);
    closure.run();
  }

  /**
   * <p>
   * Configures the project's semantic version mappings. This method is called with a closure that contains the
   * mappings. It should look like:
   * </p>
   * <pre>
   *   semanticVersions {
   *     mapping(id: "org.badver:badver:1.0.0.Final", version: "1.0.0")
   *   }
   * </pre>
   *
   * @param closure The closure that is called to set up the semantic version mappings. This closure uses the delegate
   *                class {@link SemanticVersionDelegate}.
   * @return The mappings.
   */
  public Map<String, Version> semanticVersions(@DelegatesTo(SemanticVersionDelegate.class) Closure<?> closure) {
    closure.setDelegate(new SemanticVersionDelegate(workflow.mappings, workflow.rangeMappings));
    closure.setResolveStrategy(Closure.DELEGATE_FIRST);
    closure.run();
    return workflow.mappings;
  }

  /**
   * <p>
   * Configures the standard project workflow as follows:
   * </p>
   * <pre>
   *   fetch {
   *     cache()
   *     url(url: "https://repository.lattejava.org")
   *     maven(url: "https://repo1.maven.org/maven2")
   *   }
   *   publish {
   *     cache()
   *   }
   * </pre>
   */
  public void standard() {
    String cache = LattePaths.get().cacheDir().toString();
    workflow.fetchWorkflow.processes.add(new CacheProcess(output, cache, cache, defaultMavenDir));
    workflow.fetchWorkflow.processes.add(new URLProcess(output, "https://repository.lattejava.org", null, null));
    workflow.fetchWorkflow.processes.add(new MavenProcess(output, "https://repo1.maven.org/maven2", null, null));
    workflow.publishWorkflow.processes.add(new CacheProcess(output, cache, cache, defaultMavenDir));
  }

  /**
   * Process delegate class that is used to configure {@link Process} instances for the {@link FetchWorkflow} and
   * {@link PublishWorkflow} of the {@link Workflow}.
   *
   * @author Brian Pontarelli
   */
  public static class ProcessDelegate {
    public final Output output;

    public final List<Process> processes;

    public ProcessDelegate(Output output, List<Process> processes) {
      this.output = output;
      this.processes = processes;
    }

    /**
     * Adds a {@link CacheProcess} to the workflow that uses the given attributes. Creates a Latte-only cache (latteDir
     * set, mavenDir null).
     *
     * @param attributes The attributes.
     */
    public void cache(Map<String, Object> attributes) {
      String dir = GroovyTools.toString(attributes, "dir");
      String intDir = GroovyTools.toString(attributes, "integrationDir");
      String mavenDir = GroovyTools.toString(attributes, "mavenDir");
      String cache = LattePaths.get().cacheDir().toString();
      processes.add(new CacheProcess(output,
          dir != null ? dir : cache,
          intDir != null ? intDir : cache,
          mavenDir != null ? mavenDir : defaultMavenDir));
    }

    /**
     * Adds a {@link MavenProcess} to the workflow that uses the given attributes.
     *
     * @param attributes Optionally a map that contains a URL attribute.
     */
    public void maven(Map<String, Object> attributes) {
      String url = GroovyTools.toString(attributes, "url");
      if (url == null) {
        url = "https://repo1.maven.org/maven2";
      }

      processes.add(new MavenProcess(output, url, GroovyTools.toString(attributes, "username"), GroovyTools.toString(attributes, "password")));
    }

    /**
     * Adds a {@link CacheProcess} to the workflow that handles Maven-sourced artifacts (mavenDir set, latteDir null).
     *
     * @param attributes Optionally a map that contains a dir attribute.
     */
    public void mavenCache(Map<String, Object> attributes) {
      String dir = GroovyTools.toString(attributes, "dir");
      String intDir = GroovyTools.toString(attributes, "integrationDir");
      String cache = LattePaths.get().cacheDir().toString();
      processes.add(new CacheProcess(output,
          null,
          intDir != null ? intDir : cache,
          dir != null ? dir : defaultMavenDir));
    }

    /**
     * Adds an {@link S3Process} to the workflow that uses the given attributes.
     *
     * @param attributes The S3 attributes: endpoint (required), bucket (required), accessKeyId (required),
     *                   secretAccessKey (required), region (optional, defaults to "auto").
     */
    public void s3(Map<String, Object> attributes) {
      if (!GroovyTools.hasAttributes(attributes, "endpoint", "bucket", "accessKeyId", "secretAccessKey")) {
        throw new ParseException("Invalid s3 workflow definition. It should look like:\n\n" +
            "  s3(endpoint: \"https://account-id.r2.cloudflarestorage.com\", bucket: \"my-bucket\", " +
            "accessKeyId: \"...\", secretAccessKey: \"...\")");
      }

      String region = GroovyTools.toString(attributes, "region");
      processes.add(new S3Process(output,
          GroovyTools.toString(attributes, "endpoint"),
          GroovyTools.toString(attributes, "bucket"),
          GroovyTools.toString(attributes, "accessKeyId"),
          GroovyTools.toString(attributes, "secretAccessKey"),
          region != null ? region : "auto"));
    }

    /**
     * Adds a {@link URLProcess} to the workflow that uses the given attributes.
     *
     * @param attributes The URL attributes.
     */
    public void url(Map<String, Object> attributes) {
      if (!GroovyTools.hasAttributes(attributes, "url")) {
        throw new ParseException("Invalid url workflow definition. It should look like:\n\n" +
            "  url(url: \"https://repository.lattejava.org\")");
      }

      processes.add(new URLProcess(output, GroovyTools.toString(attributes, "url"), GroovyTools.toString(attributes, "username"),
          GroovyTools.toString(attributes, "password")));
    }
  }
}
