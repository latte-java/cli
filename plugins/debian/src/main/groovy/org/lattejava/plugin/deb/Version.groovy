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
package org.lattejava.plugin.deb

import java.util.regex.Pattern

/**
 * Version for the debian package.
 *
 * @author Brian Pontarelli
 */
class Version {
  public static final Pattern UPSTREAM_VERSION_PATTERN = ~/[0-9][A-Za-z0-9.+\-:~]*/

  public static final Pattern DEBIAN_VERSION_PATTERN = ~/[A-Za-z0-9+.~]+/

  public String upstream

  public String debian

  Version(String upstream, String debian) {
    this.upstream = upstream
    this.debian = debian != null ? debian : "1"
  }

  boolean isDebianValid() {
    return DEBIAN_VERSION_PATTERN.asPredicate().test(debian)
  }

  boolean isUpstreamValid() {
    return UPSTREAM_VERSION_PATTERN.asPredicate().test(upstream) && (debian != null || !upstream.contains("-"))
  }

  public String toString() {
    StringBuilder version = new StringBuilder()
    version.append(upstream)

    if (debian.length() > 0) {
      version.append('-')
      version.append(debian)
    }

    return version.toString()
  }
}
