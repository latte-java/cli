/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.database

/**
 * The result of comparing two databases. The differences are the DDL statements that would transform the left database
 * into the right database. An empty list of differences means the databases are equal.
 *
 * @author Brian Pontarelli
 */
class DatabaseComparison {
  List<String> differences = []

  boolean areEqual() {
    return differences.empty
  }
}
