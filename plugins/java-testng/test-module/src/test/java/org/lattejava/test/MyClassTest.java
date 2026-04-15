package org.lattejava.test;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class MyClassTest {
  @Test
  public void doSomething() {
    assertEquals(new MyClass().doSomething(), "Hello World");
  }
}
