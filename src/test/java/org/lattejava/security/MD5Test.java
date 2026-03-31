/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.lattejava.security;

import java.io.IOException;
import java.nio.file.Path;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * This class tests the MD5Test.
 *
 * @author Brian Pontarelli
 */
@Test(groups = "unit")
public class MD5Test extends BaseUnitTest {
  @Test
  public void md5() throws IOException {
    Path f = projectDir.resolve("src/test/java/org/savantbuild/security/MD5Test.txt");
    MD5 md5 = MD5.forPath(f);
    assertNotNull(md5);
    assertEquals(md5.fileName, "MD5Test.txt");
    assertEquals(md5.sum, "c0bfbec19e8e5578e458ce5bfee20751");
  }
}
