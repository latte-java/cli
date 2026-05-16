/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.savantbuild.test;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Test class.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "integration")
public class MyClassIntegrationTest {
  @Test
  public void doSomething() {
    assertEquals(new MyClass().doSomething(), "Hello World");
  }
}
