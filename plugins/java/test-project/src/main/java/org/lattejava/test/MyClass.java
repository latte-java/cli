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
package org.lattejava.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java class.
 *
 * @author Brian Pontarelli
 */
public class MyClass {
  private final static Logger logger = LoggerFactory.getLogger("test");

  public String doSomething() {
    return "Hello World";
  }

  public static void main(String[] args) throws Exception {
    String markerPath = System.getProperty("latte.run.marker");
    if (markerPath == null) {
      throw new IllegalStateException("latte.run.marker system property is required");
    }
    String exitCodeStr = System.getProperty("latte.run.exitCode", "0");
    int exitCode = Integer.parseInt(exitCodeStr);

    StringBuilder sb = new StringBuilder();
    sb.append("pwd=").append(new java.io.File("").getAbsolutePath()).append('\n');
    sb.append("args=").append(String.join(",", args)).append('\n');
    sb.append("env.LATTE_RUN_TEST=").append(String.valueOf(System.getenv("LATTE_RUN_TEST"))).append('\n');
    sb.append("env.PATH.present=").append(System.getenv("PATH") != null).append('\n');

    java.nio.file.Files.writeString(java.nio.file.Path.of(markerPath), sb.toString());
    System.exit(exitCode);
  }
}
