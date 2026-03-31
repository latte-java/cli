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
package org.lattejava.dep.maven;

import java.util.Objects;

public class MavenLicense {
  public String distribution;

  public String name;

  public String url;

  public MavenLicense() {
  }

  public MavenLicense(String distribution, String name, String url) {
    this.distribution = distribution;
    this.name = name;
    this.url = url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MavenLicense)) return false;
    final MavenLicense that = (MavenLicense) o;
    return Objects.equals(distribution, that.distribution) && Objects.equals(name, that.name) && Objects.equals(url, that.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(distribution, name, url);
  }
}
