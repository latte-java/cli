/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.util;

/**
 * This class is thrown from a Graph that contains circular references that cannot be dealt with.
 *
 * @author Brian Pontarelli
 */
public class CyclicException extends RuntimeException {
  public CyclicException(String msg) {
    super(msg);
  }
}
