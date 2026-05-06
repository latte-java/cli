/*
 * Copyright (c) 2001-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.lang;

import java.util.Arrays;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests the StringTools.
 *
 * @author Brian Pontarelli
 */
public class StringToolsTest extends BaseUnitTest {
  @Test
  public void fromHex() {
    assertTrue(Arrays.equals(StringTools.fromHex("00"), new byte[]{(byte) 0}));
    assertTrue(Arrays.equals(StringTools.fromHex("C8"), new byte[]{(byte) 200}));
    assertTrue(Arrays.equals(StringTools.fromHex("EA5D"), new byte[]{(byte) 234, (byte) 93}));
  }

  @Test
  public void toHex() {
    assertEquals(StringTools.toHex((byte) 0), "00");
    assertEquals(StringTools.toHex((byte) 1), "01");
    assertEquals(StringTools.toHex((byte) 2), "02");
    assertEquals(StringTools.toHex((byte) 3), "03");
    assertEquals(StringTools.toHex((byte) 4), "04");
    assertEquals(StringTools.toHex((byte) 5), "05");
    assertEquals(StringTools.toHex((byte) 6), "06");
    assertEquals(StringTools.toHex((byte) 7), "07");
    assertEquals(StringTools.toHex((byte) 8), "08");
    assertEquals(StringTools.toHex((byte) 9), "09");
    assertEquals(StringTools.toHex((byte) 10), "0a");
    assertEquals(StringTools.toHex((byte) 11), "0b");
    assertEquals(StringTools.toHex((byte) 12), "0c");
    assertEquals(StringTools.toHex((byte) 13), "0d");
    assertEquals(StringTools.toHex((byte) 14), "0e");
    assertEquals(StringTools.toHex((byte) 15), "0f");
    assertEquals(StringTools.toHex((byte) 0), "00");
    assertEquals(StringTools.toHex((byte) 200), "c8");
    assertEquals(StringTools.toHex((byte) 234, (byte) 93), "ea5d");
  }
}
