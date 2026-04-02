/*
 * Copyright (c) 2014-2024, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep.workflow.process;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;
import org.lattejava.net.NetTools;
import org.lattejava.output.Output;
import org.lattejava.security.MD5;
import org.lattejava.security.MD5Exception;

/**
 * This class is a workflow process that attempts to download artifacts from the internet using the Latte scheme via
 * HTTP.
 * <p>
 * Latte's URL scheme is
 * <p>
 * <b>domain</b>/<b>group</b>/<b>project</b>/<b>version</b>/<b>name</b>-<b>version</b>.<b>type</b>
 *
 * @author Brian Pontarelli
 */
public class URLProcess implements Process {
  public final Output output;

  public final String password;

  public final String url;

  public final String username;

  protected final ItemSource itemSource;

  public URLProcess(Output output, String url, String username, String password) {
    this(output, url, username, password, ItemSource.LATTE);
  }

  protected URLProcess(Output output, String url, String username, String password, ItemSource itemSource) {
    this.output = output;

    Objects.requireNonNull(url, "The [url] attribute is required for the [url] workflow process");
    if (username != null || password != null) {
      Objects.requireNonNull(username, "You must specify both the [username] and [password] attributes to turn on authentication for the [url] workflow process.");
      Objects.requireNonNull(password, "You must specify both the [username] and [password] attributes to turn on authentication for the [url] workflow process.");
    }

    this.url = url;
    this.username = username;
    this.password = password;
    this.itemSource = itemSource;
  }

  /**
   * Using the URL spec given, this method connects to the URL, reads the file from the URL and stores the file in the
   * local cache store. The artifact is used to determine the local cache store directory and file name.
   *
   * @param item            The item to fetch.
   * @param publishWorkflow The publishWorkflow to publish the artifact if found.
   * @return The FetchResult of the artifact after it has been published, or null if not found.
   */
  @Override
  public FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws ProcessFailureException {
    // Try primary item first
    FetchResult result = tryFetchCandidate(item, item.item, publishWorkflow);
    if (result != null) {
      return result;
    }

    // Try alternatives
    for (String alt : item.alternativeItems) {
      result = tryFetchCandidate(item, alt, publishWorkflow);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private FetchResult tryFetchCandidate(ResolvableItem item, String candidateItem, PublishWorkflow publishWorkflow)
      throws ProcessFailureException {
    try {
      URI md5URI = NetTools.build(url, item.group.replace('.', '/'), item.project, item.version, candidateItem + ".md5");
      output.debugln("      - Download [" + md5URI + "]");
      Path md5File = downloadToPath(md5URI, username, password, null);
      if (md5File == null) {
        output.debugln("      - Not found");
        return null;
      }

      MD5 md5;
      try {
        md5 = MD5.load(md5File);
      } catch (IOException e) {
        Files.delete(md5File);
        throw new ProcessFailureException(item, e);
      }

      URI itemURI = NetTools.build(url, item.group.replace('.', '/'), item.project, item.version, candidateItem);
      output.debugln("      - Download [" + itemURI + "]");
      Path itemFile;
      try {
        itemFile = downloadToPath(itemURI, username, password, md5);
      } catch (MD5Exception e) {
        throw new MD5Exception("MD5 mismatch when fetching item from [" + itemURI + "]");
      }

      if (itemFile != null) {
        output.infoln("Downloaded [%s]", itemURI);
        ResolvableItem matchedItem = candidateItem.equals(item.item) ? item : new ResolvableItem(item, candidateItem);
        ResolvableItem md5Item = new ResolvableItem(item, candidateItem + ".md5");
        publishWorkflow.publish(new FetchResult(md5File, itemSource, md5Item));
        try {
          Path publishedFile = publishWorkflow.publish(new FetchResult(itemFile, itemSource, matchedItem));
          return new FetchResult(publishedFile != null ? publishedFile : itemFile, itemSource, matchedItem);
        } catch (ProcessFailureException e) {
          throw new ProcessFailureException(item, e);
        }
      } else {
        output.debugln("      - Not found");
      }

      return null;
    } catch (FileNotFoundException e) {
      // Special case for file:// URLs
      return null;
    } catch (IOException e) {
      throw new ProcessFailureException(item, e);
    }
  }

  /**
   * Throws an exception. This isn't supported yet.
   */
  @Override
  public Path publish(FetchResult fetchResult) throws ProcessFailureException {
    throw new ProcessFailureException("The [url] process doesn't allow publishing.");
  }

  @Override
  public String toString() {
    return "URL(" + url + ")";
  }

  private Path downloadToPath(URI uri, String username, String password, MD5 md5) throws IOException {
    try {
      return NetTools.downloadToPath(uri, username, password, md5);
    } catch (IOException e) {
      // Do not retry a FileNotFoundException
      if (e instanceof FileNotFoundException) {
        throw e;
      }

      long ms = 5_000;
      String reason = "[" + e.getClass() + "] " + e.getMessage();
      String cause = e.getCause() != null
          ? "[" + e.getCause().getClass() + "] " + e.getCause().getMessage()
          : "-";
      output.infoln(
          "      - Retry Download [" + uri + "] after [" + ms + "] ms\n" +
              "          Reason: " + reason + "\n" +
              "          Cause: " + cause);
      try {
        Thread.sleep(ms);
      } catch (InterruptedException ignore) {
      }

      return NetTools.downloadToPath(uri, username, password, md5);
    }
  }
}
