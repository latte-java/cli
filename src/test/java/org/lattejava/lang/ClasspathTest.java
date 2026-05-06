/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.lang;

import org.lattejava.lang.Classpath;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;

import static org.testng.Assert.assertEquals;

/**
 * Tests the Classpath class.
 *
 * @author Brian Pontarelli
 */
public class ClasspathTest {
  @Test
  public void string() {
    assertEquals(new Classpath().path("foo").path("bar").path(new File("baz")).path(Paths.get("fred")).toString(), "foo:bar:baz:fred");
    assertEquals(new Classpath().path("foo").path("bar").path(new File("baz")).path(Paths.get("fred")).toString("-cp "), "-cp foo:bar:baz:fred");
  }
}
