/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import java.nio.file.Path;

import org.lattejava.cli.domain.Project;
import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.PublishWorkflow;

/**
 * This interface defines a workflow process that can be used for either publishing or for fetching.
 *
 * @author Brian Pontarelli
 */
public interface Process {
  /**
   * Attempts to fetch the given item. The item is normally associated with the artifact, but might be associated with a
   * group or project. This method can use the artifact for logging or other purposes, but should use the item String
   * for fetching only.
   * <p>
   * If the item is found, it should be published by calling the {@link PublishWorkflow}.
   *
   * @param item            The item being fetched. This item name should include the necessary information so that the
   *                        process can locate the item.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return A FetchResult containing the Path to the item on the local disk and its source, or null if the item does
   *     not exist and there were no failures.
   * @throws ProcessFailureException If the process failed when fetching the artifact.
   */
  FetchResult fetch(ResolvableItem item, PublishWorkflow publishWorkflow) throws ProcessFailureException;

  /**
   * Attempts to publish the given item. The item is normally associated with the artifact, but might be associated with
   * a group or project. This method can use the artifact for logging or other purposes, but should use the item String
   * for publishing only.
   * <p>
   * If the item is published in a manner that a file can be returned, that file should be returned as it might be used
   * to create paths or other constructs.
   *
   * @param fetchResult The fetch result containing the item, file, and source.
   * @return The file if the publish process stored the given file locally (local cache for example). Otherwise, this
   *     should return null.
   * @throws ProcessFailureException If there was any issue publishing.
   */
  Path publish(FetchResult fetchResult) throws ProcessFailureException;

  /**
   * Verifies that this process is able to publish artifacts for the given project. Processes that publish to a remote
   * location should confirm the caller's credentials and permissions here, so a release can fail before any
   * irreversible step (such as creating a Git tag). The default implementation reports ready, which is correct for
   * processes that have no remote publish step (caches and fetch-only processes).
   *
   * @param project The project being released. Implementations typically use {@link Project#group} to scope the check.
   * @return A {@link PublishReadiness} describing whether this process can publish and, if not, why.
   */
  default PublishReadiness verifyPublishReadiness(Project project) {
    return PublishReadiness.READY;
  }
}
