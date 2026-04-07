/*
 * Copyright (c) 2026, Inversoft Inc., All Rights Reserved
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
package org.lattejava.cli.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.dep.domain.License;
import org.lattejava.output.Output;
/**
 * Initializes a new Latte project by prompting the user for project metadata and writing a {@code project.latte} file
 * from a template.
 * <p>
 * The template is loaded from the Latte install directory ({@code $latte.home/templates/project.latte}), or from a
 * custom path specified with the {@code --template} switch.
 *
 * @author Brian Pontarelli
 */
public class InitCommand implements Command {
  private final Scanner scanner;

  public InitCommand() {
    this(new Scanner(System.in));
  }

  InitCommand(Scanner scanner) {
    this.scanner = scanner;
  }

  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    run(configuration, output, Path.of(""));
  }

  /**
   * Runs the init command using the given directory as the project root. This overload exists for testing.
   *
   * @param configuration The runtime configuration.
   * @param output        The output for user interaction and logging.
   * @param projectDir    The directory to create the project file in.
   */
  public void run(RuntimeConfiguration configuration, Output output, Path projectDir) {
    Path projectFile = projectDir.resolve("project.latte");
    if (Files.isRegularFile(projectFile)) {
      throw new RuntimeFailureException("project.latte already exists in this directory.");
    }

    String group = promptGroup(output);
    String name = promptName(output, projectDir);
    String license = promptLicense(output);

    String template = loadTemplate(configuration);
    String content = template
        .replace("${group}", group)
        .replace("${name}", name)
        .replace("${license}", license);

    try {
      Files.writeString(projectFile, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to write project.latte: " + e.getMessage());
    }

    createDirectoryLayout(projectDir);
    output.infoln("Created project.latte for [%s:%s]", group, name);
  }

  private void createDirectoryLayout(Path projectDir) {
    try {
      Files.createDirectories(projectDir.resolve("src/main/java"));
      Files.createDirectories(projectDir.resolve("src/main/resources"));
      Files.createDirectories(projectDir.resolve("src/test/java"));
      Files.createDirectories(projectDir.resolve("src/test/resources"));
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to create project directory layout: " + e.getMessage());
    }
  }

  private String promptGroup(Output output) {
    while (true) {
      output.info("Group (e.g., org.example): ");
      String input = scanner.nextLine().trim();
      if (input.isEmpty()) {
        output.errorln("Group cannot be empty.");
        continue;
      }
      if (!input.matches("[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)*")) {
        output.errorln("Invalid group [%s]. Must be a dot-separated identifier (e.g., org.example).", input);
        continue;
      }
      return input;
    }
  }

  private String promptName(Output output, Path projectDir) {
    String defaultName = guessProjectName(projectDir);
    while (true) {
      if (defaultName != null) {
        output.info("Project name [%s]: ", defaultName);
      } else {
        output.info("Project name: ");
      }

      String input = scanner.nextLine().trim();
      if (input.isEmpty() && defaultName != null) {
        return defaultName;
      }
      if (input.isEmpty()) {
        output.errorln("Project name cannot be empty.");
        continue;
      }
      if (!input.matches("[a-zA-Z][a-zA-Z0-9-]*")) {
        output.errorln("Invalid project name [%s]. Must start with a letter and contain only letters, digits, and hyphens.", input);
        continue;
      }
      return input;
    }
  }

  private String promptLicense(Output output) {
    while (true) {
      output.info("License (SPDX identifier e.g. Apache-2.0) [MIT]: ");
      String input = scanner.nextLine().trim();
      if (input.isEmpty()) {
        return "MIT";
      }
      if (!License.Licenses.containsKey(input)) {
        output.errorln("Unknown license [%s]. Use an SPDX identifier (e.g., Apache-2.0, MIT, GPL-3.0-only).", input);
        continue;
      }
      return input;
    }
  }

  private String guessProjectName(Path projectDir) {
    Path resolved = projectDir.toAbsolutePath().normalize();
    Path fileName = resolved.getFileName();
    if (fileName == null) {
      return null;
    }

    String name = fileName.toString();
    if (name.matches("[a-zA-Z][a-zA-Z0-9-]*")) {
      return name;
    }

    return null;
  }

  private String loadTemplate(RuntimeConfiguration configuration) {
    // Check for --template switch override
    if (configuration.switches.valueSwitches.containsKey("template")) {
      Path customTemplate = Path.of(configuration.switches.valueSwitches.get("template").getFirst());
      if (!Files.isRegularFile(customTemplate)) {
        throw new RuntimeFailureException("Template file not found: " + customTemplate);
      }
      try {
        return Files.readString(customTemplate, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeFailureException("Failed to read template [" + customTemplate + "]: " + e.getMessage());
      }
    }

    // Load from the Latte install directory
    String latteHome = System.getProperty("latte.home");
    if (latteHome == null) {
      throw new RuntimeFailureException("The latte.home system property is not set. Is Latte installed correctly?");
    }

    Path dataTemplate = Path.of(latteHome, "templates", "project.latte");
    if (!Files.isRegularFile(dataTemplate)) {
      throw new RuntimeFailureException("Template not found at [" + dataTemplate + "]. Is Latte installed correctly?");
    }

    try {
      return Files.readString(dataTemplate, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to read template [" + dataTemplate + "]: " + e.getMessage());
    }
  }
}
