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
package org.lattejava.dep.domain;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Domain for license exceptions (like the classpath exception).
 *
 * @author Brian Pontarelli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LicenseTextException {
  @JsonProperty("detailsUrl")
  public String detailsURL;

  @JsonProperty("licenseExceptionId")
  public String identifier;

  public String name;

  public String reference;

  public String text;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LicenseTextException)) return false;
    final LicenseTextException that = (LicenseTextException) o;
    return Objects.equals(identifier, that.identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier);
  }
}
