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
package org.lattejava.io;

import java.nio.file.Paths;
import java.util.HashSet;

import org.lattejava.BaseUnitTest;
import org.testng.annotations.Test;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

/**
 * Tests the FileInfo class.
 *
 * @author Brian Pontarelli
 */
public class FileInfoTest extends BaseUnitTest {
  @Test
  public void toMode() {
    FileInfo info = new FileInfo(Paths.get(""), Paths.get(""));
    info.permissions = new HashSet<>(asList(GROUP_EXECUTE, OTHERS_EXECUTE, OWNER_EXECUTE));
    assertEquals(info.toMode(), 0b1_000_000_001_001_001);

    info.permissions = new HashSet<>(asList(GROUP_WRITE, OTHERS_WRITE, OWNER_WRITE));
    assertEquals(info.toMode(), 0b1_000_000_010_010_010);

    info.permissions = new HashSet<>(asList(GROUP_READ, OTHERS_READ, OWNER_READ));
    assertEquals(info.toMode(), 0b1_000_000_100_100_100);

    info.permissions = new HashSet<>(asList(GROUP_EXECUTE));
    assertEquals(info.toMode(), 0b1_000_000_000_001_000);

    info.permissions = new HashSet<>(asList(GROUP_EXECUTE, GROUP_READ));
    assertEquals(info.toMode(), 0b1_000_000_000_101_000);

    info.permissions = new HashSet<>(asList(GROUP_EXECUTE, GROUP_READ, GROUP_WRITE));
    assertEquals(info.toMode(), 0b1_000_000_000_111_000);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE));
    assertEquals(info.toMode(), 0b1_000_000_001_000_000);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE, OWNER_READ));
    assertEquals(info.toMode(), 0b1_000_000_101_000_000);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE));
    assertEquals(info.toMode(), 0b1_000_000_111_000_000);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE, GROUP_EXECUTE));
    assertEquals(info.toMode(), 0b1_000_000_001_001_000);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE, OWNER_READ, GROUP_READ));
    assertEquals(info.toMode(), 0b1_000_000_101_100_000);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE, OWNER_READ, GROUP_READ, OTHERS_EXECUTE));
    assertEquals(info.toMode(), 0b1_000_000_101_100_001);

    info.permissions = new HashSet<>(asList(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE, GROUP_EXECUTE, GROUP_READ, GROUP_WRITE, OTHERS_EXECUTE, OTHERS_READ, OTHERS_WRITE));
    assertEquals(info.toMode(), 0b1_000_000_111_111_111);
  }
}
