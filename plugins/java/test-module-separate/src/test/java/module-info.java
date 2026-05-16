/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
module org.lattejava.test.tests {
  requires org.lattejava.test;
  requires org.testng;
  opens org.lattejava.test.tests to org.testng;
}
