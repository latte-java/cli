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

import org.lattejava.dep.domain.ResolvableItem;

/**
 * Thrown when a process encounters a failure (network failure, IO exception, etc.).
 *
 * @author Brian Pontarelli
 */
public class ProcessFailureException extends RuntimeException {
  public ProcessFailureException(ResolvableItem item) {
    super("A process failed for the artifact [" + item + "].");
  }

  public ProcessFailureException(ResolvableItem item, Throwable cause) {
    super("A process failed for the artifact [" + item + "]." + (cause != null ? " The original error is [" + cause.getMessage() + "]\n" : "\n"), cause);
  }

  public ProcessFailureException(String message) {
    super(message);
  }

  public ProcessFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
