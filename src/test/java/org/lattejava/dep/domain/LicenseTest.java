/*
 * Copyright (c) 2022-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.domain;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

/**
 * Tests the license object.
 *
 * @author Brian Pontarelli
 */
public class LicenseTest {
  @Test
  public void parse() {
    assertEquals(License.parse("ApacheV1_0", null).identifier, "Apache-1.0");
    assertEquals(License.parse("Apache-1.0", null).identifier, "Apache-1.0");
    assertSame(License.parse("ApacheV1_0", null), License.parse("Apache-1.0", null));
    assertEquals(License.parse("Commercial", "Text").text, "Text");
    assertEquals(License.parse("BSD-2-Clause", "Text").text, "Text");
    assertEquals(License.parse("GPL-2.0 WITH Classpath-exception-2.0", null).exception.identifier, "Classpath-exception-2.0");
    assertEquals(License.parse("GPL-2.0 WITH Classpath-exception-2.0", "Text").text, "Text");

    try {
      License.parse("bad", null);
      fail("Should have failed");
    } catch (Exception e) {
      // Expected
    }

    try {
      License.parse("Commercial", null);
      fail("Should have failed");
    } catch (Exception e) {
      // Expected
    }

    try {
      License.parse("Commercial", "     ");
      fail("Should have failed");
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  public void equality() {
    assertEquals(License.parse("GPL-2.0", null), License.parse("GPL-2.0", "Custom")); // Custom text doesn't matter for SPDX
    assertNotEquals(License.parse("GPL-2.0", null), License.parse("GPL-3.0", null));
    assertEquals(License.parse("GPL-2.0 WITH Classpath-exception-2.0", null), License.parse("GPL-2.0 WITH Classpath-exception-2.0", null));
    assertEquals(License.parse("GPL-2.0 WITH Classpath-exception-2.0", null), License.parse("GPL-2.0 WITH Classpath-exception-2.0", "Custom"));
    assertNotEquals(License.parse("GPL-2.0 WITH Classpath-exception-2.0", null), License.parse("GPL-3.0 WITH Classpath-exception-2.0", null));
    assertEquals(License.parse("Other", "Custom"), License.parse("Other", "Custom"));
    assertNotEquals(License.parse("Other", "Custom"), License.parse("Other", "Custom 1"));
    assertEquals(License.parse("BSD_2_Clause", "Text"), License.parse("BSD-2-Clause", null));
  }
}
