/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.test;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Test class.
 *
 * @author Brian Pontarelli
 */
public class MyClassTest {
  @Test
  public void doSomething() {
    assertEquals(new MyClass().doSomething(), "Hello World");
  }
}
