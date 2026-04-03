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
package org.lattejava.dep.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.lattejava.dep.domain.ResolvableItem;
import org.lattejava.dep.workflow.process.FetchResult;
import org.lattejava.dep.workflow.process.Process;
import org.lattejava.dep.workflow.process.ProcessFailureException;
import org.lattejava.output.Output;
import org.lattejava.security.ChecksumException;

/**
 * This class is the workflow that is used when attempting to fetch artifacts.
 *
 * @author Brian Pontarelli
 */
public class FetchWorkflow {
  public final List<Process> processes = new ArrayList<>();

  private final Output output;

  public FetchWorkflow(Output output, Process... processes) {
    this.output = output;
    Collections.addAll(this.processes, processes);
  }

  /**
   * This loops over all the processes until the item is found or not. Each process must call to the PublishWorkflow if
   * it finds the artifact and the publish workflow must be able to return a File that can be used for future
   * reference.
   *
   * @param item            The item being fetched. This item name should include the necessary information to locate
   *                        the item.
   * @param publishWorkflow The PublishWorkflow that is used to store the item if it can be found.
   * @return A FetchResult that contains the item file and source, or null if the item was not found.
   * @throws ProcessFailureException If any of the processes failed while attempting to fetch the artifact.
   * @throws ChecksumException If the item's checksum file did not match the item.
   */
  public FetchResult fetchItem(ResolvableItem item, PublishWorkflow publishWorkflow)
      throws ProcessFailureException, ChecksumException {
    output.debugln("\nFetching [" + item + "]");
    return processes.stream()
                    .map((process) -> {
                      output.debugln(" * [" + process.getClass().getSimpleName() + ".fetch]");
                      return process.fetch(item, publishWorkflow);
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
  }
}
