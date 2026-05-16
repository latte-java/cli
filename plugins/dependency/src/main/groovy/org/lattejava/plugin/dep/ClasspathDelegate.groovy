/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.lattejava.dep.DependencyService
import org.lattejava.dep.graph.ResolvedArtifactGraph
import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.lang.Classpath
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.RuntimeFailureException

import java.nio.file.Path

/**
 * Classpath delegate for closures.
 *
 * @author Brian Pontarelli
 */
class ClasspathDelegate extends BaseDependencyDelegate {
  public static final String ERROR_MESSAGE = "The dependency plugin classpath method must be called like this:\n\n" +
      "  dependency.classpath {\n" +
      "    path(location: \"some-directory\")\n" +
      "    path(location: Paths.get(\"some-otherdirectory\"))\n" +
      "  }"

  private final DependencyService dependencyService

  List<Path> paths = new ArrayList<>()

  ClasspathDelegate(Project project, DependencyService dependencyService) {
    super(project)
    this.dependencyService = dependencyService
  }

  /**
   * Adds a path to the classpath. This is called with name attributes (location is required) like this:
   *
   * <pre>
   *   path(location: "some-directory")
   * </pre>
   *
   * @param attributes The named attributes.
   * @return The Path.
   */
  Path path(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["location"], ["location"], [:])) {
      throw new RuntimeFailureException(ERROR_MESSAGE)
    }

    def location = attributes["location"]
    def path = FileTools.toPath(location)
    paths.add(project.directory.resolve(path).toAbsolutePath())
    return path
  }

  /**
   * Converts the delegate to a {@link Classpath} instance.
   *
   * @return The Classpath.
   */
  Classpath toClasspath() {
    Classpath classpath
    if (traversalRules != null && traversalRules.rules.size() > 0 && project.dependencies != null) {
      ResolvedArtifactGraph graph = dependencyService.resolve(project.artifactGraph, project.workflow, traversalRules)
      classpath = graph.toClasspath()
    } else {
      classpath = new Classpath()
    }

    if (paths.size() > 0) {
      paths.each { path -> classpath.path(path) }
    }

    return classpath
  }
}
