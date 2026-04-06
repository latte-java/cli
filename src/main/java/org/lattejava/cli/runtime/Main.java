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
package org.lattejava.cli.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import org.lattejava.cli.parser.DefaultTargetGraphBuilder;
import org.lattejava.cli.parser.ParseException;
import org.lattejava.cli.parser.groovy.GroovyProjectFileParser;
import org.lattejava.cli.plugin.PluginLoadException;
import org.lattejava.dep.DependencyTreePrinter;
import org.lattejava.dep.LicenseException;
import org.lattejava.dep.PublishException;
import org.lattejava.dep.domain.CompatibilityException;
import org.lattejava.dep.graph.DependencyGraph;
import org.lattejava.dep.graph.DependencyGraph.Dependency;
import org.lattejava.dep.workflow.ArtifactMetaDataMissingException;
import org.lattejava.dep.workflow.ArtifactMissingException;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.domain.VersionException;
import org.lattejava.output.Output;
import org.lattejava.output.SystemOutOutput;
import org.lattejava.security.ChecksumException;
import org.lattejava.util.CyclicException;

import static java.util.Collections.singletonList;

/**
 * Main entry point for Latte CLI runtime.
 *
 * @author Brian Pontarelli
 */
public class Main {
  public static Path projectDir = Paths.get("");

  /**
   * The main method.
   *
   * @param args CLI arguments.
   */
  public static void main(String... args) {
    RuntimeConfigurationParser runtimeConfigurationParser = new DefaultRuntimeConfigurationParser();
    RuntimeConfiguration runtimeConfiguration = runtimeConfigurationParser.parse(args);
    Output output = new SystemOutOutput(runtimeConfiguration.colorizeOutput);
    if (runtimeConfiguration.debug) {
      output.enableDebug();
    }

    try {
      Runner runner = new DefaultRunner(output, new GroovyProjectFileParser(output, new DefaultTargetGraphBuilder()), new DefaultProjectRunner(output));
      runner.run(projectDir, runtimeConfiguration);
    } catch (CompatibilityException e) {
      printCompatibilityError(e, output);
      int lineNumber = determineLineNumber(e);
      output.errorln(e.getMessage() + (lineNumber > 0 ? " Error occurred on line [" + lineNumber + "]" : ""));
      output.debug(e);
      System.exit(1);
    } catch (ArtifactMetaDataMissingException | ArtifactMissingException | RunException | RuntimeFailureException |
             LicenseException | ChecksumException | ParseException | PluginLoadException | ProcessFailureException |
             PublishException | VersionException e) {
      int lineNumber = determineLineNumber(e);
      output.errorln(e.getMessage() + (lineNumber > 0 ? " Error occurred on line [" + lineNumber + "]" : ""));
      output.debug(e);
      System.exit(1);
    } catch (CyclicException e) {
      output.errorln("Your dependencies appear to have cycle. The root message is [" + e.getMessage() + "]");
      output.debug(e);
      System.exit(1);
    } catch (Throwable t) {
      output.errorln("Build failed due to an exception or error." + (runtimeConfiguration.debug ? "" : " Enable debug using the %s switch to see the stack trace."), RuntimeConfiguration.DEBUG_SWITCH);
      output.debug(t);
      System.exit(1);
    }
  }

  /**
   * @param e      The compatibility exception.
   * @param output The output.
   */
  public static void printCompatibilityError(CompatibilityException e, Output output) {
    DependencyGraph graph = e.graph;
    Dependency incompatible = e.dependency;
    DependencyTreePrinter.print(output, graph, null, new HashSet<>(singletonList(incompatible)));
  }

  public static void printHelp(Output output) {
    output.infoln("Usage: latte [switches] <command | targets>");
    output.infoln("");
    output.infoln("Commands:");
    output.infoln("");
    output.infoln("   init            Initializes a new Latte project in the current directory");
    output.infoln("                   Options: --template=<path>  Use a custom project template");
    output.infoln("");
    output.infoln("Switches:");
    output.infoln("");
    output.infoln("   --noColor       Disables the colorized output of Latte");
    output.infoln("   --debug         Enables debug output");
    output.infoln("   --help          Displays the help message");
    output.infoln("   --listTargets   Lists the build targets");
    output.infoln("   --version       Prints the version of Latte");
    output.infoln("");
    output.infoln("NOTE: If any other argument starts with '--' then it is considered a switch. Switches can optionally have values using the equals sign like this:");
    output.infoln("");
    output.infoln("   --switch");
    output.infoln("   --switch=value");
    output.infoln("");
  }

  public static void printVersion(Output output) {
    String version = Main.class.getPackage().getImplementationVersion();
    output.infoln("Latte Build System Version [" + version + "]");
    output.infoln("");
  }

  private static int determineLineNumber(Exception e) {
    for (int i = 0; i < e.getStackTrace().length; i++) {
      StackTraceElement ste = e.getStackTrace()[i];
      if (ste.getFileName() != null && ste.getFileName().endsWith(".latte")) {
        return ste.getLineNumber();
      }
    }

    return -1;
  }
}
