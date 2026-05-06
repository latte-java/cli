/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.cli.domain;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * This class defines a target within the project file.
 *
 * @author Brian Pontarelli
 */
public class Target {
  public List<String> dependencies;

  public String description;

  public Runnable invocation;

  public String name;

  public Target() {
  }

  public Target(String name, String description, Runnable invocation, String... dependencies) {
    this.name = name;
    this.description = description;
    this.invocation = invocation;
    this.dependencies = asList(dependencies);
  }
}
