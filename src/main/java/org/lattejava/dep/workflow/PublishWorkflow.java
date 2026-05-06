/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.process.FetchResult;
import org.lattejava.dep.workflow.process.ItemSource;
import org.lattejava.dep.workflow.process.Process;
import org.lattejava.dep.workflow.process.ProcessFailureException;

/**
 * This is the interface that defines how artifacts are published to different locations during resolution. Publishing
 * is the act of storing the artifact for later use. In general the publishing corresponds one-to-one with the local
 * cache store locations that are used as part of the {@link FetchWorkflow}, but this is in no way required.
 *
 * @author Brian Pontarelli
 */
public class PublishWorkflow {
  public final List<Process> processes = new ArrayList<>();

  public PublishWorkflow(Process... processes) {
    Collections.addAll(this.processes, processes);
  }

  /**
   * @return The process list.
   */
  public List<Process> getProcesses() {
    return processes;
  }

  /**
   * Publishes the item using the processes in this workflow.
   *
   * @param fetchResult The fetch result containing the item, file, and source.
   * @return A file that can be used to reference the artifact for paths and other constructs.
   * @throws ProcessFailureException If the artifact could not be published for any reason.
   */
  public Path publish(FetchResult fetchResult) throws ProcessFailureException {
    Path result = null;
    for (Process process : processes) {
      Path temp = process.publish(fetchResult);
      if (result == null) {
        result = temp;
      }
    }

    return result;
  }

  /**
   * Publishes a negative file for the item. This file is empty, but signals Latte not to attempt to fetch that specific
   * item again, since it doesn't exist.
   *
   * @param item   The item that the negative is being published for.
   * @param source The source to tag the negative marker with.
   */
  public void publishNegative(ResolvableItem item, ItemSource source) {
    Path itemFile;
    try {
      File tempFile = File.createTempFile("latte-item", "neg");
      tempFile.deleteOnExit();
      itemFile = tempFile.toPath();
    } catch (IOException e) {
      // This is okay, because negatives are only for performance and if we can't create one, we'll just
      // head out and try and fetch it again next time.
      return;
    }

    for (Process process : processes) {
      try {
        ResolvableItem negItem = new ResolvableItem(item, item.item + ".neg");
        process.publish(new FetchResult(itemFile, source, negItem));
      } catch (ProcessFailureException e) {
        // Continue since this is okay.
      }
    }
  }
}
