/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.lattejava.cli.command;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.lattejava.cli.domain.Project;
import org.lattejava.cli.runtime.Main;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.cli.runtime.RuntimeConfiguration;
import org.lattejava.cli.runtime.RuntimeFailureException;
import org.lattejava.io.FileTools;
import org.lattejava.io.tar.TarTools;
import org.lattejava.output.Output;

/**
 * Upgrades the Latte runtime, plugins, or dependencies.
 *
 * @author Brian Pontarelli
 */
public class UpgradeCommand implements Command {
  private static final HttpClient httpClient = HttpClient.newBuilder()
                                                         .connectTimeout(Duration.ofSeconds(10))
                                                         .followRedirects(HttpClient.Redirect.NORMAL)
                                                         .build();

  @Override
  public void run(RuntimeConfiguration configuration, Output output, Project project) {
    if (configuration.args.isEmpty()) {
      printHelp(output);
      return;
    }

    String subcommand = configuration.args.getFirst();
    switch (subcommand) {
      case "help" -> printHelp(output);
      case "runtime" -> upgradeRuntime(output);
      case "plugins" -> upgradePlugins(output, project);
      case "dependency" -> upgradeDependency(configuration, output, project);
      case "dependencies" -> upgradeDependencies(output, project);
      case "all" -> {
        upgradeRuntime(output);
        if (project != null) {
          upgradePlugins(output, project);
          upgradeDependencies(output, project);
        }
      }
      default -> throw new RuntimeFailureException("Unknown upgrade parameter [" + subcommand + "]. Run 'latte upgrade help' for usage.");
    }
  }

  private void printHelp(Output output) {
    output.infoln("Usage: latte upgrade <parameter>");
    output.infoln("");
    output.infoln("Parameters:");
    output.infoln("");
    output.infoln("   all              Upgrades the runtime, dependencies, and all plugins");
    output.infoln("   runtime          Upgrades only the Latte runtime");
    output.infoln("   plugins          Upgrades all plugins in the project file");
    output.infoln("   dependency       Upgrades a single dependency");
    output.infoln("                    Usage: latte upgrade dependency <group:name:version>");
    output.infoln("   dependencies     Upgrades all project dependencies");
    output.infoln("   help             Displays this help message");
    output.infoln("");
  }

  private void upgradeRuntime(Output output) {
    String latteHome = System.getProperty("latte.home");
    if (latteHome == null || latteHome.isBlank()) {
      throw new RuntimeFailureException("The 'latte.home' system property is not set. Cannot determine install directory.");
    }

    String currentVersion = Main.class.getPackage().getImplementationVersion();

    // Query GitHub Releases API for the latest release
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create("https://api.github.com/repos/latte-java/cli/releases/latest"))
                                     .header("Accept", "application/vnd.github+json")
                                     .GET()
                                     .build();

    try {
      HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeFailureException("Failed to query GitHub Releases API. Status code [" + response.statusCode() + "].");
      }

      JSONParser parser = new JSONParser();
      JSONObject release = (JSONObject) parser.parse(response.body());
      String latestVersion = (String) release.get("tag_name");
      if (latestVersion == null) {
        throw new RuntimeFailureException("GitHub release is missing the 'tag_name' field.");
      }

      if (latestVersion.equals(currentVersion)) {
        output.infoln("Latte runtime is already up to date [" + currentVersion + "].");
        return;
      }

      // Find the .tar.gz asset
      JSONArray assets = (JSONArray) release.get("assets");
      String downloadUrl = null;
      if (assets != null) {
        for (Object obj : assets) {
          JSONObject asset = (JSONObject) obj;
          String name = (String) asset.get("name");
          if (name != null && name.endsWith(".tar.gz")) {
            downloadUrl = (String) asset.get("browser_download_url");
            break;
          }
        }
      }

      if (downloadUrl == null) {
        throw new RuntimeFailureException("No .tar.gz asset found in the latest GitHub release.");
      }

      output.infoln("Upgrading Latte runtime from [" + currentVersion + "] to [" + latestVersion + "]...");

      // Download the tarball
      HttpRequest downloadRequest = HttpRequest.newBuilder()
                                               .uri(URI.create(downloadUrl))
                                               .GET()
                                               .build();

      HttpResponse<InputStream> downloadResponse = httpClient.send(downloadRequest, BodyHandlers.ofInputStream());
      if (downloadResponse.statusCode() != 200) {
        throw new RuntimeFailureException("Failed to download release tarball. Status code [" + downloadResponse.statusCode() + "].");
      }

      Path tempTarball = Files.createTempFile("latte-upgrade-", ".tar.gz");
      Path tempDir = Files.createTempDirectory("latte-upgrade-");

      try {
        Files.copy(downloadResponse.body(), tempTarball, StandardCopyOption.REPLACE_EXISTING);

        // Extract the tarball
        TarTools.untar(tempTarball, tempDir, false, false);

        // Replace contents of latte.home
        Path homeDir = Path.of(latteHome);
        Path binDir = homeDir.resolve("bin");
        Path libDir = homeDir.resolve("lib");
        Path templatesDir = homeDir.resolve("templates");

        if (Files.exists(binDir)) {
          FileTools.prune(binDir);
        }
        if (Files.exists(libDir)) {
          FileTools.prune(libDir);
        }
        if (Files.exists(templatesDir)) {
          FileTools.prune(templatesDir);
        }

        // Move new directories in — the tarball may contain a top-level directory
        Path extractedRoot = tempDir;
        try (var stream = Files.list(tempDir)) {
          var entries = stream.toList();
          if (entries.size() == 1 && Files.isDirectory(entries.getFirst())) {
            extractedRoot = entries.getFirst();
          }
        }

        Path newBin = extractedRoot.resolve("bin");
        Path newLib = extractedRoot.resolve("lib");
        Path newTemplates = extractedRoot.resolve("templates");

        if (Files.exists(newBin)) {
          Files.move(newBin, binDir);
        }
        if (Files.exists(newLib)) {
          Files.move(newLib, libDir);
        }
        if (Files.exists(newTemplates)) {
          Files.move(newTemplates, templatesDir);
        }

        output.infoln("Successfully upgraded Latte runtime to [" + latestVersion + "].");
      } finally {
        Files.deleteIfExists(tempTarball);
        if (Files.exists(tempDir)) {
          FileTools.prune(tempDir);
        }
      }
    } catch (RuntimeFailureException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeFailureException("Failed to upgrade Latte runtime.", e);
    }
  }

  private void upgradePlugins(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'plugins' upgrade requires a project.latte file.");
    }

    Path projectFile = project.directory.resolve("project.latte");
    String content;
    try {
      content = Files.readString(projectFile, java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeFailureException("Failed to read project.latte: " + e.getMessage());
    }

    // Pattern: loadPlugin(id: "group:name:version")
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "(loadPlugin\\(id:\\s*\"([^:]+:[^:]+):)([^\"]+)(\"\\))");
    java.util.regex.Matcher matcher = pattern.matcher(content);
    StringBuilder result = new StringBuilder();
    boolean updated = false;

    while (matcher.find()) {
      String artifactId = matcher.group(2);
      String currentVersion = matcher.group(3);
      String latestVersion = queryLatestVersion(artifactId);

      if (latestVersion != null && !latestVersion.equals(currentVersion)) {
        output.infoln("Upgrading plugin [%s] from %s to %s", artifactId, currentVersion, latestVersion);
        matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
            matcher.group(1) + latestVersion + matcher.group(4)));
        updated = true;
      } else {
        matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
        if (latestVersion == null) {
          output.infoln("Plugin [%s:%s] not found in repository, skipping", artifactId, currentVersion);
        } else {
          output.infoln("Plugin [%s] already at latest version %s", artifactId, currentVersion);
        }
      }
    }
    matcher.appendTail(result);

    if (updated) {
      try {
        Files.writeString(projectFile, result.toString(), java.nio.charset.StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeFailureException("Failed to write project.latte: " + e.getMessage());
      }
    }
  }

  /**
   * Queries the Latte repository search API for the latest version of an artifact.
   *
   * @return The latest version string, or null if not found.
   */
  private String queryLatestVersion(String artifactId) {
    try {
      String encodedId = java.net.URLEncoder.encode(artifactId, java.nio.charset.StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.lattejava.org/repository/search?id=" + encodedId + "&latest=true"))
          .GET()
          .timeout(Duration.ofMillis(10_000))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404) {
        return null;
      }
      if (response.statusCode() != 200) {
        return null;
      }
      JSONObject json = (JSONObject) new JSONParser().parse(response.body());
      JSONArray versions = (JSONArray) json.get("versions");
      if (versions == null || versions.isEmpty()) {
        return null;
      }
      return (String) versions.getFirst();
    } catch (Exception e) {
      return null;
    }
  }

  private void upgradeDependency(RuntimeConfiguration configuration, Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependency' upgrade requires a project.latte file.");
    }
    if (configuration.args.size() < 2) {
      throw new RuntimeFailureException("Usage: latte upgrade dependency <group:name:version>");
    }

    String dependencySpec = configuration.args.get(1);
    Artifact artifact;
    try {
      artifact = new Artifact(dependencySpec);
    } catch (Exception e) {
      throw new RuntimeFailureException("Invalid dependency [" + dependencySpec + "]. Expected format: group:name:version");
    }

    if (project.dependencies == null) {
      throw new RuntimeFailureException("No dependencies found in project.latte.");
    }

    boolean found = false;
    for (DependencyGroup group : project.dependencies.groups.values()) {
      for (int i = 0; i < group.dependencies.size(); i++) {
        Artifact existing = group.dependencies.get(i);
        if (existing.id.equals(artifact.id)) {
          group.dependencies.set(i, artifact);
          found = true;
          output.infoln("Upgrading [%s] from %s to %s in [%s] group", artifact.id, existing.version, artifact.version, group.name);
          break;
        }
      }
    }

    if (!found) {
      throw new RuntimeFailureException("Dependency [" + artifact.id + "] not found in any dependency group.");
    }

    Path projectFile = project.directory.resolve("project.latte");
    InstallCommand.replaceDependenciesBlock(projectFile, project.dependencies);
  }

  private void upgradeDependencies(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependencies' upgrade requires a project.latte file.");
    }
    if (project.dependencies == null) {
      output.infoln("No dependencies found in project.latte.");
      return;
    }

    boolean updated = false;
    for (DependencyGroup group : project.dependencies.groups.values()) {
      for (int i = 0; i < group.dependencies.size(); i++) {
        Artifact existing = group.dependencies.get(i);
        String artifactId = existing.id.group + ":" + existing.id.project;
        String latestVersion = queryLatestVersion(artifactId);

        if (latestVersion != null && !latestVersion.equals(existing.version.toString())) {
          output.infoln("Upgrading [%s] from %s to %s in [%s] group", artifactId, existing.version, latestVersion, group.name);
          group.dependencies.set(i, new Artifact(artifactId + ":" + latestVersion));
          updated = true;
        } else if (latestVersion == null) {
          output.infoln("Dependency [%s:%s] not found in repository, skipping", artifactId, existing.version);
        } else {
          output.infoln("Dependency [%s] already at latest version %s", artifactId, existing.version);
        }
      }
    }

    if (updated) {
      Path projectFile = project.directory.resolve("project.latte");
      InstallCommand.replaceDependenciesBlock(projectFile, project.dependencies);
    }
  }
}
