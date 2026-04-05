/*
 * Copyright (c) 2024, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.linter

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.lattejava.dep.DependencyService
import org.lattejava.dep.domain.Artifact
import org.lattejava.dep.domain.Dependencies
import org.lattejava.dep.domain.DependencyGroup
import org.lattejava.dep.domain.License
import org.lattejava.dep.domain.ReifiedArtifact
import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration

import net.sourceforge.pmd.PMDConfiguration
import net.sourceforge.pmd.PMDVersion
import net.sourceforge.pmd.PmdAnalysis
import net.sourceforge.pmd.lang.LanguageRegistry
import net.sourceforge.pmd.lang.rule.RulePriority
import net.sourceforge.pmd.lang.rule.RuleSetLoader
import net.sourceforge.pmd.renderers.HTMLRenderer
import net.sourceforge.pmd.renderers.TextRenderer

/**
 * PMD plugin.
 *
 * @author Daniel DeGroff
 */
class LinterPlugin extends BaseGroovyPlugin {
  public LinterSettings settings = new LinterSettings()

  LinterPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
  }

  private void writeReport(String fileName, String extension, StringWriter writer) {
    String fileNameWithSuffix = fileName + extension;
    Files.write(settings.reportDirectory.resolve(fileNameWithSuffix), writer.toString().getBytes(StandardCharsets.UTF_8))
  }

  void pmd(Map attributes = [:]) {
    String reportFormat = attributes.getOrDefault("reportFormat", "xml")
    String reportFileName = attributes.getOrDefault("reportFileName", "pmd-report")
    boolean reportSuppressedViolations = attributes.getOrDefault("reportSuppressedViolations", false)
    String languageVersion = attributes.getOrDefault("languageVersion", "17")
    String inputPath = attributes.getOrDefault("inputPath", "src/main/java")
    String sourceClassesPath = attributes.getOrDefault("sourceClassesPath", "build/classes/main")
    String testClassesPath = attributes.getOrDefault("testClassesPath", "build/classes/test")
    String cachePath = attributes.getOrDefault("cachePath", "build/pmd.cache")
    String minimumPriority = attributes.getOrDefault("minimumPriority", "MEDIUM")
    String[] ruleSets = attributes.getOrDefault("ruleSets", [])
    boolean failOnViolations = attributes.getOrDefault("failOnViolations", true)
    List<String> customRuleDependencySpecs = attributes.getOrDefault("customRuleDependencySpecs", [])
    // our baseline rule class loader we can get from a PMD class itself
    def ruleClassLoader = RuleSetLoader.class.getClassLoader()
    if (customRuleDependencySpecs) {
      ruleClassLoader = getLoaderWithCustomRules(customRuleDependencySpecs, ruleClassLoader)
    }

    if (ruleSets.length == 0) {
      fail("You must specify one or more values for the [ruleSets] argument. It will look something like this:\n\npmd(ruleSets: [\"src/test/resources/pmd/ruleset.xml\"])")
    }

    // Ensure the report directory exists
    FileTools.prune(settings.reportDirectory)
    if (!Files.exists(settings.reportDirectory.parent)) {
      Files.createDirectories(settings.reportDirectory.parent);
    }
    Files.createDirectory(settings.reportDirectory)

    def config = getPMDConfiguration(cachePath, inputPath, sourceClassesPath, testClassesPath,
        languageVersion, minimumPriority, reportSuppressedViolations, reportFormat, reportFileName)

    def writers = [
        "html": new StringWriter(),
        "text": new StringWriter()
    ]

    def html = new HTMLRenderer()
    html.setWriter(writers.html)
    html.setShowSuppressedViolations(reportSuppressedViolations)

    def text = new TextRenderer()
    text.setWriter(writers.text)
    text.setShowSuppressedViolations(reportSuppressedViolations)

    def projectPath = project.directory.toAbsolutePath().toString() + "/"
    try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
      for (ruleSet in ruleSets) {
        String ruleSetFileName = projectPath + ruleSet
        pmd.addRuleSet(pmd.newRuleSetLoader()
            .loadResourcesWith(ruleClassLoader)
            .loadFromResource(ruleSetFileName))
      }

      pmd.addRenderer(html)
      pmd.addRenderer(text)

      output.infoln("\nPMD analysis configuration ")
      output.infoln("===============================================")
      output.infoln("Language: Java %s", languageVersion)
      output.infoln("Minimum priority: %s", minimumPriority)
      output.infoln("Rulesets: %s", String.join(", ", ruleSets))

      var report = pmd.performAnalysisAndCollectReport()

      writeReport(reportFileName, ".html", writers.html)
      writeReport(reportFileName, ".txt", writers.text)

      output.infoln("\nPMD analysis summary ")
      output.infoln("===============================================")
      output.infoln("[%d] Configuration errors", report.configurationErrors.size())
      output.infoln("[%d] ProcessingErrors errors", report.processingErrors.size())
      output.infoln("[%d] Suppressed violations", report.suppressedViolations.size())
      output.infoln("[%d] Violations", report.violations.size())

      if (report.configurationErrors.size() > 0 || report.processingErrors.size() > 0 || (reportSuppressedViolations && report.suppressedViolations.size() > 0) || report.violations.size() > 0) {
        output.infoln("\nPMD analysis report")
        output.infoln("===============================================")
        output.infoln writers.text.toString()
      }

      if (report.configurationErrors.size() > 0 || report.processingErrors.size() > 0 || (failOnViolations && report.violations.size() > 0)) {
        fail("PMD analysis failed.")
      }
    }
  }

  private getPMDConfiguration(String cachePath, String inputPath, String sourceClassesPath, String testClassesPath,
                              String languageVersion, String minimumPriority, boolean reportSuppressedViolations,
                              String reportFormat, String reportFileName) {
    new PMDConfiguration().with {
      output.infoln("Using PMD version [%s]", PMDVersion.fullVersionName)
      setAnalysisCacheLocation(project.directory.resolve(cachePath).toAbsolutePath().toString());
      addInputPath(project.directory.resolve(inputPath))
      def auxClassPath = [
          sourceClassesPath,
          testClassesPath
      ].collect { f -> project.directory.resolve(f).toAbsolutePath().toString() }
          .join(File.pathSeparator)
      output.infoln("Adding aux classpath [%s]", auxClassPath)
      prependAuxClasspath(auxClassPath)

      setDefaultLanguageVersion(LanguageRegistry.PMD.getLanguageById("java").getVersion(languageVersion))
      setMinimumPriority(RulePriority.valueOf(minimumPriority))
      setShowSuppressedViolations(reportSuppressedViolations)
      setReportFormat(reportFormat)
      setReportFile(settings.reportDirectory.resolve((String) (reportFileName + "." + reportFormat)))
      it
    }
  }

  private getLoaderWithCustomRules(List<String> customRuleDependencySpecs,
                                   ClassLoader ruleClassLoader) {
    def ourOwnRuleGroup = "rules"
    // without the traversal rules, when we resolve below, Savant won't know which groups to resolve
    def traversalRules = new DependencyService.TraversalRules().with(ourOwnRuleGroup,
        new DependencyService.TraversalRules.GroupTraversalRule(false, false))
    // a "fake" or artificial root, just to resolve the custom rule JARs

    def root = new ReifiedArtifact("org.lattejava.plugin:linter-custom-rule:0.0.0",
        License.Licenses.get("ApacheV2_0"))

    // When an app uses the linter plugin, they've supplied a list of dependency specs, each spec
    // pointing to custom rules
    Artifact[] dependencies = customRuleDependencySpecs.collect { spec ->
      new Artifact(spec)
    }.toArray(new Artifact[0])

    // we are just using the project to get this dependency. This does NOT mean we are inter-tangled
    // with the project's dependency graph
    def dependencyService = project.dependencyService
    def dependencyGraph = dependencyService.buildGraph(root,
        new Dependencies(new DependencyGroup(ourOwnRuleGroup, false, dependencies)),
        project.workflow)
    def artifactGraph = dependencyService.reduce(dependencyGraph)
    def resolvedGraph = dependencyService.resolve(artifactGraph,
        project.workflow,
        traversalRules)

    URL[] ruleJARURLs = resolvedGraph.toClasspath()
        .paths
        .collect { p -> p.toUri().toURL() }.toArray(new URL[0])

    output.infoln("Including the following custom rules in the PMD classpath: [%s]",
        ruleJARURLs)

    new URLClassLoader(ruleJARURLs, ruleClassLoader)
  }
}
