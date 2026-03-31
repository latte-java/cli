/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
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
