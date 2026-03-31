/*
 * Copyright (c) 2022, Inversoft Inc., All Rights Reserved
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
package org.lattejava.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lattejava.domain.Version.PreRelease;
import org.lattejava.domain.Version.PreRelease.PreReleasePart.NumberPreReleasePart;
import org.lattejava.domain.Version.PreRelease.PreReleasePart.StringPreReleasePart;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

/**
 * Version Tester.
 *
 * @author Brian Pontarelli
 */
public class VersionTest {
  @Test
  public void compare() throws Exception {
    // Test identity
    Assert.assertEquals(new Version("1.1.0").compareTo(new Version("1.1.0")), 0);

    // Test everything else
    assertCompareTo("2", "1");
    assertCompareTo("1.8", "1.7");
    assertCompareTo("1.8.1", "1.8.0");

    assertCompareTo("1.8.0-beta", "1.8.0-1");
    assertCompareTo("1.8.0-beta", "1.8.0-alpha");
    assertCompareTo("1.8.0-beta.2", "1.8.0-alpha");
    assertCompareTo("1.8.0-beta.2", "1.8.0-beta");
    assertCompareTo("1.8.0-beta.2", "1.8.0-beta.1");

    assertCompareTo("1.8.0-beta.2.build.2", "1.8.0-alpha");
    assertCompareTo("1.8.0-beta.2.build.2", "1.8.0-beta");
    assertCompareTo("1.8.0-beta.2.build.2", "1.8.0-beta.1");
    assertCompareTo("1.8.0-beta.2.build.2", "1.8.0-beta.2.build.1");
    assertCompareTo("1.8.0-beta.3-{integration}", "1.8.0-beta.2-{integration}");

    assertCompareTo("1.8.0-b", "1.8.0-a");
    assertCompareTo("1.8.0-b.b", "1.8.0-a.a");
    assertCompareTo("1.8.0-b.b", "1.8.0-a.b");
    assertCompareTo("1.8.0-b.b", "1.8.0-b.a");

    assertCompareTo("1.8.0-b.b.b", "1.8.0-a");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-a.a");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-a.a.a");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-a.b.b");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-b");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-b.a");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-b.b");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-b.a.b");
    assertCompareTo("1.8.0-b.b.b", "1.8.0-b.b.a");

    assertCompareTo("1.8.0-2", "1.8.0-1");
    assertCompareTo("1.8.0-2.2", "1.8.0-2.1");
    assertCompareTo("1.8.0-2.2.2", "1.8.0-2.2.1");
    assertCompareTo("1.8.0-2.2.2.2", "1.8.0-2.2.2.1");

    List<Version> versions = new ArrayList<>(asList(new Version("3.0"), new Version("1.7"), new Version("1.0"), new Version("2.0"), new Version("1.8"), new Version("1.6-alpha"), new Version("1.6")));
    Collections.sort(versions);
    Assert.assertEquals(versions, asList(new Version("1.0"), new Version("1.6-alpha"), new Version("1.6"), new Version("1.7"), new Version("1.8"), new Version("2.0"), new Version("3.0")));
  }

  @Test
  public void equals() {
    // Test identity
    Assert.assertEquals(new Version("1.1.0"), new Version("1.1.0"));

    // Test parts
    assertVersionEquals("1", 1, 0, 0, null);
    assertVersionEquals("1.1", 1, 1, 0, null);
    assertVersionEquals("1.2.6", 1, 2, 6, null);
    assertVersionEquals("1.2.6-alpha", 1, 2, 6, new PreRelease(new StringPreReleasePart("alpha")));
    assertVersionEquals("1.2.6-alpha.beta", 1, 2, 6, new PreRelease(new StringPreReleasePart("alpha"), new StringPreReleasePart("beta")));
    assertVersionEquals("1.2.6-alpha.beta.foo", 1, 2, 6, new PreRelease(new StringPreReleasePart("alpha"), new StringPreReleasePart("beta"), new StringPreReleasePart("foo")));
    assertVersionEquals("1.2.6-1-2.beta.foo", 1, 2, 6, new PreRelease(new StringPreReleasePart("1-2"), new StringPreReleasePart("beta"), new StringPreReleasePart("foo")));
    assertVersionEquals("1.2.6-1-2.3-4.5-6", 1, 2, 6, new PreRelease(new StringPreReleasePart("1-2"), new StringPreReleasePart("3-4"), new StringPreReleasePart("5-6")));
    assertVersionEquals("1.2.6-1", 1, 2, 6, new PreRelease(new NumberPreReleasePart(1)));
    assertVersionEquals("1.2.6-1.2", 1, 2, 6, new PreRelease(new NumberPreReleasePart(1), new NumberPreReleasePart(2)));
    assertVersionEquals("1.2.6-1.2.3", 1, 2, 6, new PreRelease(new NumberPreReleasePart(1), new NumberPreReleasePart(2), new NumberPreReleasePart(3)));
    assertVersionEquals("1.2.6-alpha.1.build.2", 1, 2, 6, new PreRelease(new StringPreReleasePart("alpha"), new NumberPreReleasePart(1), new StringPreReleasePart("build"), new NumberPreReleasePart(2)));
  }

  @Test
  public void ints() throws Exception {
    Version v = new Version(1, 1, 2, null, null);
    Assert.assertEquals(1, v.major);
    Assert.assertEquals(1, v.minor);
    Assert.assertEquals(2, v.patch);

    v = new Version(0, 0, 0, null, null);
    Assert.assertEquals(0, v.major);
    Assert.assertEquals(0, v.minor);
    Assert.assertEquals(0, v.patch);

    try {
      new Version(-1, 0, 0, null, null);
      Assert.fail("Should have failed");
    } catch (Exception e) {
    }

    try {
      new Version(0, -1, 0, null, null);
      Assert.fail("Should have failed");
    } catch (Exception e) {
    }

    try {
      new Version(0, 0, -1, null, null);
      Assert.fail("Should have failed");
    } catch (Exception e) {
    }
  }

  @Test
  public void isIntegration() {
    Assert.assertTrue(new Version("1.2.3-{integration}").isIntegration());
    Assert.assertTrue(new Version("1.2.3-beta.{integration}").isIntegration());
    Assert.assertFalse(new Version("1.2.3-beta-{integration}").isIntegration());
    Assert.assertTrue(new Version("1.2.3-beta.2.{integration}").isIntegration());
    Assert.assertFalse(new Version("1.2.3-beta.2-{integration}").isIntegration());
    Assert.assertFalse(new Version("1.2.3-beta.2+{integration}").isIntegration());
  }

  @Test
  public void string() throws Exception {
    assertVersion("10.100.2000", 10, 100, 2000, false, false, true, false, false, null, null);
    assertVersion("0.0.0", 0, 0, 0, true, false, false, false, false, null, null);
    assertVersion("17", 17, 0, 0, true, false, false, false, false, null, null);
    assertVersion("3.4", 3, 4, 0, false, true, false, false, false, null, null);
    assertVersion("3.4.8", 3, 4, 8, false, false, true, false, false, null, null);

    assertVersion("3-RC1", 3, 0, 0, false, false, false, true, false, new PreRelease(new StringPreReleasePart("RC1")), null);
    assertVersion("3.4-RC1", 3, 4, 0, false, false, false, true, false, new PreRelease(new StringPreReleasePart("RC1")), null);
    assertVersion("3.4.5-RC1", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("RC1")), null);

    assertVersion("3.4.5-beta", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("beta")), null);
    assertVersion("3.4.5-beta.1", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("beta"), new NumberPreReleasePart(1)), null);
    assertVersion("3.4.5-beta.2", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("beta"), new NumberPreReleasePart(2)), null);
    assertVersion("3.4.5-beta.2.build.4", 3, 4, 5, false, false, false, true, false,
        new PreRelease(new StringPreReleasePart("beta"), new NumberPreReleasePart(2), new StringPreReleasePart("build"), new NumberPreReleasePart(4)), null);
    assertVersion("3.4.5-pre-beta.2.build.4", 3, 4, 5, false, false, false, true, false,
        new PreRelease(new StringPreReleasePart("pre-beta"), new NumberPreReleasePart(2), new StringPreReleasePart("build"), new NumberPreReleasePart(4)), null);
    assertVersion("3.4.5-1-2.2", 3, 4, 5, false, false, false, true, false,
        new PreRelease(new StringPreReleasePart("1-2"), new NumberPreReleasePart(2)), null);

    assertVersion("3.4.5-beta+metaData", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("beta")), "metaData");
    assertVersion("3.4.5-beta.1+49393", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("beta"), new NumberPreReleasePart(1)), "49393");
    assertVersion("3.4.5-beta.2+foobar", 3, 4, 5, false, false, false, true, false, new PreRelease(new StringPreReleasePart("beta"), new NumberPreReleasePart(2)), "foobar");
    assertVersion("3.4.5-beta.2.build.4+30930927", 3, 4, 5, false, false, false, true, false,
        new PreRelease(new StringPreReleasePart("beta"), new NumberPreReleasePart(2), new StringPreReleasePart("build"), new NumberPreReleasePart(4)), "30930927");
    assertVersion("3.4.5-pre-beta.2.build.4+sha.f938de838ab", 3, 4, 5, false, false, false, true, false,
        new PreRelease(new StringPreReleasePart("pre-beta"), new NumberPreReleasePart(2), new StringPreReleasePart("build"), new NumberPreReleasePart(4)), "sha.f938de838ab");
    assertVersion("3.4.5-1-2.2+meta-data", 3, 4, 5, false, false, false, true, false,
        new PreRelease(new StringPreReleasePart("1-2"), new NumberPreReleasePart(2)), "meta-data");

    assertVersion("3.4.5+metaData", 3, 4, 5, false, false, true, false, false, null, "metaData");
    assertVersion("3.4.5+49393", 3, 4, 5, false, false, true, false, false, null, "49393");
    assertVersion("3.4.5+foobar", 3, 4, 5, false, false, true, false, false, null, "foobar");
    assertVersion("3.4.5+30930927", 3, 4, 5, false, false, true, false, false, null, "30930927");
    assertVersion("3.4.5+sha.f938de838ab", 3, 4, 5, false, false, true, false, false, null, "sha.f938de838ab");
    assertVersion("3.4.5+meta-data", 3, 4, 5, false, false, true, false, false, null, "meta-data");

    assertBadVersion("-1.0.0");
    assertBadVersion("1.0.0.0");
    assertBadVersion("1.0.0.0.0");
    assertBadVersion("0.-1.0");
    assertBadVersion("0.0.-1");
    assertBadVersion("1.0.0-");
    assertBadVersion("1.0.0+");
    assertBadVersion("1.0.0.");
    assertBadVersion("-1.0.0-");
    assertBadVersion("+1.0.0+");
    assertBadVersion(".1.0.0.");
    assertBadVersion("-1.0.0");
    assertBadVersion("+1.0.0");
    assertBadVersion(".1.0.0");
    assertBadVersion("foo");
    assertBadVersion("0foo0foo0");
    assertBadVersion("foo.0.0");
    assertBadVersion("0.foo.0");
    assertBadVersion("0.0.foo");
  }

  @Test
  public void toIntegration() {
    Assert.assertTrue(new Version("1.2.3").toIntegrationVersion().isIntegration());
    Assert.assertEquals(new Version("1.2.3").toIntegrationVersion(), new Version("1.2.3-{integration}"));
    Assert.assertTrue(new Version("1.2.3-beta").toIntegrationVersion().isIntegration());
    Assert.assertEquals(new Version("1.2.3-beta").toIntegrationVersion(), new Version("1.2.3-beta.{integration}"));
    Assert.assertTrue(new Version("1.2.3-beta.2").toIntegrationVersion().isIntegration());
    Assert.assertEquals(new Version("1.2.3-beta.2").toIntegrationVersion(), new Version("1.2.3-beta.2.{integration}"));
  }

  private void assertBadVersion(String spec) {
    try {
      new Version(spec);
      Assert.fail("Should have failed");
    } catch (Exception e) {
    }
  }

  private void assertCompareTo(String spec1, String spec2) {
    Version v1 = new Version(spec1);
    Version v2 = new Version(spec2);
    int comparison = v1.compareTo(v2);
    Assert.assertTrue(comparison > 0);
    comparison = v2.compareTo(v1);
    Assert.assertTrue(comparison < 0);

    v1 = new Version(spec1);
    v2 = new Version(spec2 + "+bMetaData");
    comparison = v1.compareTo(v2);
    Assert.assertTrue(comparison > 0);
    comparison = v2.compareTo(v1);
    Assert.assertTrue(comparison < 0);

    v1 = new Version(spec1 + "+aMetaData");
    v2 = new Version(spec2);
    comparison = v1.compareTo(v2);
    Assert.assertTrue(comparison > 0);
    comparison = v2.compareTo(v1);
    Assert.assertTrue(comparison < 0);

    v1 = new Version(spec1 + "+aMetaData");
    v2 = new Version(spec2 + "+bMetaData");
    comparison = v1.compareTo(v2);
    Assert.assertTrue(comparison > 0);
    comparison = v2.compareTo(v1);
    Assert.assertTrue(comparison < 0);
  }

  private void assertVersion(String spec, int major, int minor, int patch, boolean isMajor, boolean isMinor,
                             boolean isPatch,
                             boolean isPreRelease, boolean isIntegration, PreRelease preRelease, String metaData) {
    Version v = new Version(spec);
    Assert.assertEquals(v.major, major);
    Assert.assertEquals(v.minor, minor);
    Assert.assertEquals(v.patch, patch);
    Assert.assertEquals(v.isMajor(), isMajor);
    Assert.assertEquals(v.isMinor(), isMinor);
    Assert.assertEquals(v.isPatch(), isPatch);
    Assert.assertEquals(v.isPreRelease(), isPreRelease);
    Assert.assertEquals(v.isIntegration(), isIntegration);
    Assert.assertEquals(v.preRelease, preRelease);
    Assert.assertEquals(v.metaData, metaData);
  }

  private void assertVersionEquals(String spec1, int major, int minor, int patch, PreRelease preRelease) {
    Version v1 = new Version(spec1);
    Version v2 = new Version(major, minor, patch, preRelease, null);
    Assert.assertEquals(v1, v2);

    v1 = new Version(spec1 + "+aMetaData");
    Assert.assertEquals(v1, v2);

    v1 = new Version(spec1);
    v2 = new Version(major, minor, patch, preRelease, "bMetaData");
    Assert.assertEquals(v1, v2);

    v1 = new Version(spec1 + "+aMetaData");
    v2 = new Version(major, minor, patch, preRelease, "bMetaData");
    Assert.assertEquals(v1, v2);
  }
}
