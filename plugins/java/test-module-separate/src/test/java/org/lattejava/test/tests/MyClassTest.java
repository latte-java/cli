package org.lattejava.test.tests;

import org.lattejava.test.MyClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MyClassTest {
  @Test
  public void doSomething() {
    assertEquals(new MyClass().doSomething(), "Hello World");
  }
}
