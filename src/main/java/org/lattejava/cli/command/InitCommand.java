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

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

import org.lattejava.cli.domain.*;
import org.lattejava.cli.runtime.*;
import org.lattejava.dep.domain.*;
import org.lattejava.output.*;

/**
 * Initializes a new Latte project by prompting the user for project metadata and scaffolding the files from a template
 * directory.
 * <p>
 * The template directory is resolved from the first positional argument. A bare name (e.g. {@code library},
 * {@code web}) is looked up under {@code $latte.home/templates/<name>}; a value containing a path separator, starting
 * with {@code ~}, or an absolute path is treated as a filesystem path. When no argument is supplied, the
 * {@code library} template is used.
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

  private static List<PendingFile> collectFiles(Path templateDir, Path projectDir, Map<String, String> vars) {
    List<PendingFile> results = new ArrayList<>();
    try (var stream = Files.walk(templateDir)) {
      stream.filter(Files::isRegularFile).forEach(source -> {
        String relative = templateDir.relativize(source).toString();
        String resolved = substitute(relative, vars);
        Path target = projectDir.resolve(resolved);
        results.add(new PendingFile(source, target, resolved));
      });
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to walk template directory [" + templateDir + "]: " + e.getMessage());
    }
    return results;
  }

  private static Map<String, String> deriveVariables(String group, String name, String license) {
    String nameId = name.replace('-', '_');
    String packageName = group + "." + nameId;
    String packagePath = packageName.replace('.', '/');
    return Map.of(
        "group", group,
        "name", name,
        "license", license,
        "nameId", nameId,
        "package", packageName,
        "packagePath", packagePath
    );
  }

  private static Path expandTilde(String arg) {
    if (arg.equals("~")) {
      return Path.of(System.getProperty("user.home"));
    }
    if (arg.startsWith("~/") || arg.startsWith("~\\")) {
      return Path.of(System.getProperty("user.home"), arg.substring(2));
    }
    return Path.of(arg);
  }

  private static boolean isPathLike(String arg) {
    return arg.contains("/") || arg.contains("\\") || arg.startsWith("~") || Path.of(arg).isAbsolute();
  }

  private static String substitute(String input, Map<String, String> vars) {
    String result = input;
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    Path projectDir = project != null ? project.directory : Path.of("");

    Path templateDir = resolveTemplateDir(configuration);

    String group = promptGroup(output);
    String name = promptName(output, projectDir);
    String license = promptLicense(output);

    Map<String, String> vars = deriveVariables(group, name, license);

    copyTemplate(templateDir, projectDir, vars);

    output.infoln("Created project [%s:%s] from template [%s]", group, name, templateDir.getFileName());
  }

  private void copyTemplate(Path templateDir, Path projectDir, Map<String, String> vars) {
    List<PendingFile> pending = collectFiles(templateDir, projectDir, vars);

    for (PendingFile file : pending) {
      if (Files.isRegularFile(file.target)) {
        throw new RuntimeFailureException("[" + file.relativeResolved + "] already exists");
      }
    }

    for (PendingFile file : pending) {
      try {
        String content = Files.readString(file.source, StandardCharsets.UTF_8);
        String substituted = substitute(content, vars);
        Path parent = file.target.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.writeString(file.target, substituted, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeFailureException("Failed to write [" + file.target + "]: " + e.getMessage());
      }
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

  private Path resolveTemplateDir(RuntimeConfiguration configuration) {
    String arg = configuration.args.isEmpty() ? null : configuration.args.getFirst();

    if (arg != null && isPathLike(arg)) {
      Path customDir = expandTilde(arg);
      if (!Files.isDirectory(customDir)) {
        throw new RuntimeFailureException("Template directory not found: [" + customDir + "]");
      }
      return customDir;
    }

    String templateName = arg != null ? arg : "library";
    String latteHome = System.getProperty("latte.home");
    if (latteHome == null) {
      throw new RuntimeFailureException("The latte.home system property is not set. Is Latte installed correctly?");
    }

    Path namedDir = Path.of(latteHome, "templates", templateName);
    if (!Files.isDirectory(namedDir)) {
      throw new RuntimeFailureException("Template [" + templateName + "] not found at [" + namedDir + "]. Is Latte installed correctly?");
    }
    return namedDir;
  }

  private record PendingFile(Path source, Path target, String relativeResolved) {
  }
}
