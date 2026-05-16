/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals

/**
 * An integration test
 *
 * @author Brian Pontarelli
 */
@Test(groups = "integration")
class MyClassIntegrationTest {
  @Test
  def simple() {
    MyClass myClass = new MyClass()
    assertEquals(myClass.doSomething("frank"), "frank did something")
  }
}
