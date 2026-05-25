/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.lattejava.cli.auth.CredentialStore;
import org.lattejava.cli.auth.Tokens;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.output.Output;
import org.lattejava.util.LattePaths;

/**
 * A publish-only workflow process that publishes artifacts to the Latte repository through its authenticated publish
 * API. Rather than holding S3/R2 credentials, it uses the OAuth tokens stored by {@code latte login}: it asks the API
 * for a short-lived presigned PUT URL for each item and uploads the bytes to that URL.
 * <p>
 * Objects use the standard Latte layout {@code {group}/{project}/{version}/{item}} with group dots replaced by slashes.
 * {@link #fetch} is unsupported (returns {@code null}); fetching the public repository is handled by the
 * {@link S3Process}/{@link URLProcess}.
 *
 * @author Brian Pontarelli
 */
public class LatteProcess implements Process {
  public static final String DEFAULT_API_URL = "https://api.lattejava.org";

  private static final HttpClient httpClient = HttpClient.newBuilder()
                                                         .connectTimeout(Duration.ofSeconds(10))
                                                         .followRedirects(HttpClient.Redirect.NORMAL)
                                                         .build();

  public final String apiURL;

  public final Output output;

  private final PublishAPIClient client;

  private final Path configFile;

  /**
   * Creates a new LatteProcess using the default global configuration file and HTTP client.
   *
   * @param output The output for logging.
   * @param apiURL The base URL of the publish API.
   */
  public LatteProcess(Output output, String apiURL) {
    this(output, apiURL, LattePaths.get().configDir().resolve("config.properties"), new PublishAPIClient(apiURL, httpClient));
  }

  LatteProcess(Output output, String apiURL, Path configFile, PublishAPIClient client) {
    this.output = output;
    this.apiURL = apiURL;
    this.configFile = configFile;
    this.client = client;
  }

  private static String objectKey(ResolvableItem item) {
    return item.group.replace('.', '/') + "/" + item.project + "/" + item.version + "/" + item.item;
  }

  @Override
  public FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    return null;
  }

  @Override
  public Path publish(FetchResult fetchResult) throws ProcessFailureException {
    ResolvableItem item = fetchResult.item();
    String key = objectKey(item);

    CredentialStore credentialStore = new CredentialStore(configFile);
    Tokens tokens = credentialStore.load();
    if (tokens.accessToken() == null) {
      throw new ProcessFailureException("You are not logged in to the Latte repository. Run [latte login] before publishing.");
    }

    byte[] body;
    try {
      body = Files.readAllBytes(fetchResult.file());
    } catch (IOException e) {
      throw new ProcessFailureException(item, e);
    }

    PublishAPIClient.PresignResponse response = client.requestPresignedURL(item.group, key, tokens);
    if (response.refreshedTokens() != null) {
      credentialStore.store(response.refreshedTokens());
    }

    client.upload(response.url(), body);
    output.infoln("Published [%s] to the Latte repository [%s]", item, key);

    return null;
  }

  @Override
  public String toString() {
    return "Latte(" + apiURL + ")";
  }
}
