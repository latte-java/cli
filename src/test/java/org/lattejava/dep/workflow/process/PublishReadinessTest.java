/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.dep.workflow.process;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the PublishReadiness result type and its factory methods.
 *
 * @author Brian Pontarelli
 */
public class PublishReadinessTest extends BaseUnitTest {
  @Test
  public void factories() {
    PublishReadiness ready = PublishReadiness.READY;
    assertTrue(ready.ready());
    assertNull(ready.message());

    PublishReadiness notReady = PublishReadiness.notReady("You cannot publish.");
    assertFalse(notReady.ready());
    assertEquals(notReady.message(), "You cannot publish.");
  }
}
