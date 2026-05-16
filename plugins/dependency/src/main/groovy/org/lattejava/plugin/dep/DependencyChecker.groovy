/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.dep

import org.objectweb.asm.ClassReader
import org.lattejava.dep.domain.ResolvedArtifact
import org.lattejava.dep.graph.ResolvedArtifactGraph
import org.lattejava.cli.domain.Project
import org.lattejava.output.Output

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

/**
 *
 * @author Brian Pontarelli
 */
class DependencyChecker {
  private final Output output

  private final DependencyPlugin plugin

  private final Project project

  DependencyChecker(Project project, Output output, DependencyPlugin plugin) {
    this.output = output
    this.project = project
    this.plugin = plugin
  }

  Set<ResolvedArtifact> check(Path buildDir, List<String> dependencyGroups) {
    Set<String> projectClasses = new HashSet<>()
    Path mainBuildDir = project.directory.resolve(buildDir)
    if (Files.notExists(mainBuildDir)) {
      fail("Missing build output directory [${buildDir}]")
    }

    Files.walk(mainBuildDir).forEach({ path ->
      if (path.toString().endsWith(".class")) {
        ImportClassVisitor importClassVisitor = new ImportClassVisitor()
        new ClassReader(Files.readAllBytes(path)).accept(importClassVisitor, ClassReader.SKIP_FRAMES)
        projectClasses.addAll(importClassVisitor.classes)
        output.debugln("Class [%s] was processed and depends on the classes %s", path, importClassVisitor.classes)
      }
    })

    output.debugln("Classes that the project uses are %s", projectClasses)

    ResolvedArtifactGraph resolvedArtifactGraph = plugin.resolve() {
      dependencyGroups.each { group ->
        dependencies(group: group, transitive: false, fetchSource: false)
      }
    }

    Set<ResolvedArtifact> unused = new HashSet<>()
    if (resolvedArtifactGraph.size() == 0) {
      return unused
    }

    resolvedArtifactGraph.traverse(resolvedArtifactGraph.root, true, null, { origin, destination, edgeValue, depth, isLast ->
      output.debugln("Checking compile dependency [%s] at [%s]", destination, destination.file)

      Set<String> dependencyClasses = new HashSet<>()
      JarFile jarFile = new JarFile(destination.file.toFile())
      jarFile.entries().each { entry ->
        if (entry.name.endsWith(".class")) {
          output.debugln("Handling JAR file entry [%s]", entry.name)
          dependencyClasses.add(entry.name.substring(0, entry.name.length() - 6).replace(".", "/"))
        }
      }
      jarFile.close()

      output.debugln("Classes that the dependency [%s] provides are %s", destination, dependencyClasses)
      if (!dependencyClasses.removeAll(projectClasses)) {
        output.infoln("Unused dependency [%s]", destination)
        unused.add(destination)
      }

      return false
    })

    return unused
  }
}
