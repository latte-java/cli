/*
 * Copyright (c) 2013-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals

/**
 * Test
 *
 * @author Brian Pontarelli
 */
class MyClassTest {
  @Test
  def simple() {
    MyClass myClass = new MyClass()
    assertEquals(myClass.doSomething("frank"), "frank did something")
  }
}
