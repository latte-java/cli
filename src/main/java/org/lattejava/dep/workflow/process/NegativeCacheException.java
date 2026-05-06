/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import org.lattejava.dep.domain.ResolvableItem;

/**
 * This class denotes that a negative cache was stored for an artifact item of some sort and that it should not be
 * resolved again.
 *
 * @author Brian Pontarelli
 */
public class NegativeCacheException extends ProcessFailureException {
  public NegativeCacheException(ResolvableItem item) {
    super(item);
  }
}
