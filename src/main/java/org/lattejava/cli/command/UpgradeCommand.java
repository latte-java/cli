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
    throw new RuntimeFailureException("Plugin upgrade not yet implemented.");
  }

  private void upgradeDependency(RuntimeConfiguration configuration, Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependency' upgrade requires a project.latte file.");
    }
    throw new RuntimeFailureException("Dependency upgrade not yet implemented.");
  }

  private void upgradeDependencies(Output output, Project project) {
    if (project == null) {
      throw new RuntimeFailureException("The 'dependencies' upgrade requires a project.latte file.");
    }
    throw new RuntimeFailureException("Dependencies upgrade not yet implemented.");
  }
}
