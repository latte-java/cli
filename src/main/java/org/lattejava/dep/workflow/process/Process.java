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
package org.lattejava.dep.workflow.process;

import java.nio.file.Path;

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
}
