/*
 * Copyright (c) 2013-2017, Inversoft Inc., All Rights Reserved
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.lattejava.dep.LicenseException;

/**
 * Domain for licenses.
 *
 * @author Brian Pontarelli
 */
public final class License {
  public static final Set<String> CustomLicenses = new HashSet<>();

  public static final Map<String, LicenseTextException> Exceptions = new HashMap<>();

  public static final Map<String, License> Licenses = new HashMap<>();

  public boolean customText;

  public String detailsURL;

  public LicenseTextException exception;

  public boolean fsfLibre;

  public String identifier;

  public String name;

  public boolean osiApproved;

  public String reference;

  public List<String> seeAlso;

  public String text;

  /**
   * For testing and JSON handling only.
   */
  public License() {
  }

  public License(License other) {
    this.customText = other.customText;
    this.detailsURL = other.detailsURL;
    this.exception = other.exception;
    this.fsfLibre = other.fsfLibre;
    this.identifier = other.identifier;
    this.name = other.name;
    this.osiApproved = other.osiApproved;
    this.reference = other.reference;
    this.seeAlso = other.seeAlso;
    this.text = other.text;
  }

  private License(License other, LicenseTextException exception, String text) {
    this(other);
    this.customText = text != null && !text.isBlank();
    this.exception = exception;

    if (this.customText) {
      this.text = text;
    }
  }

  /**
   * Creates a commercial or non-standard license.
   *
   * @param identifier The required license id.
   * @param text       The required license text.
   */
  private License(String identifier, String text) {
    boolean badId = !CustomLicenses.contains(identifier) && !Licenses.containsKey(identifier);
    if (text == null || text.isBlank() || badId) {
      throw new LicenseException(identifier);
    }

    this.customText = true;
    this.identifier = identifier;
    this.text = text;
  }

  /**
   * Tries to determine the license using a URL.
   *
   * @param url The URL of the license text.
   * @return The License if it can be found or null if it doesn't exist.
   */
  public static License lookupByURL(String url) {
    if (url == null) {
      return null;
    }

    String httpsURL = url.replace("http:", "https:");
    String httpURL = url.replace("https:", "http:");
    for (License license : Licenses.values()) {
      if (license.seeAlso.contains(url) ||
          license.seeAlso.stream()
                         .anyMatch(seeAlso -> seeAlso.startsWith(httpsURL) || seeAlso.startsWith(httpURL) || httpsURL.startsWith(seeAlso) || httpURL.startsWith(seeAlso))) {
        return license;
      }
    }

    return null;
  }

  /**
   * Parses a Latte SPDX identifier to determine the type of license. Latte supports additional license types and
   * flexible license text.
   *
   * @param identifier The Latte SPDX id.
   * @param text       (Optional) The license text.
   * @return The license and never null
   * @throws LicenseException If the identifier was invalid.
   */
  public static License parse(String identifier, String text) throws LicenseException {
    boolean isCustom = CustomLicenses.contains(identifier);
    if (isCustom && text != null) {
      return new License(identifier, text);
    }

    if (isCustom) {
      throw new LicenseException(identifier);
    }

    String[] parts = identifier.split("\\s+WITH\\s+");
    License license = Licenses.get(parts[0]);
    if (license == null) {
      throw new LicenseException(parts[0]);
    }

    LicenseTextException exception = null;
    if (parts.length == 2) {
      exception = Exceptions.get(parts[1]);
      if (exception == null) {
        throw new LicenseException(parts[1]);
      }
    }

    if (exception == null && (text == null || text.isBlank())) {
      return license;
    }

    return new License(license, exception, text);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof final License license)) {
      return false;
    }

    // If the license is SPDX, then all that matters is the id and the exception
    if (Licenses.containsKey(identifier)) {
      return Objects.equals(identifier, license.identifier) && Objects.equals(exception, license.exception);
    }

    // Otherwise, the text is the main component if everything else is the same
    return Objects.equals(identifier, license.identifier) && Objects.equals(exception, license.exception) && Objects.equals(text, license.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier);
  }

  public String toString() {
    return identifier;
  }

  private static Boolean toBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    return false;
  }

  private static List<String> toStringList(JSONArray array) {
    if (array == null) {
      return new ArrayList<>();
    }
    List<String> list = new ArrayList<>();
    for (Object obj : array) {
      list.add((String) obj);
    }
    return list;
  }

  static {
    try (InputStream is = License.class.getResourceAsStream("/licenses.json")) {
      JSONParser parser = new JSONParser();
      JSONObject root = (JSONObject) parser.parse(new InputStreamReader(is, StandardCharsets.UTF_8));
      JSONArray licensesArray = (JSONArray) root.get("licenses");
      for (Object obj : licensesArray) {
        JSONObject licObj = (JSONObject) obj;
        License license = new License();
        license.reference = (String) licObj.get("reference");
        license.detailsURL = (String) licObj.get("detailsUrl");
        license.name = (String) licObj.get("name");
        license.identifier = (String) licObj.get("licenseId");
        license.osiApproved = toBoolean(licObj.get("isOsiApproved"));
        license.fsfLibre = toBoolean(licObj.get("isFsfLibre"));
        license.seeAlso = toStringList((JSONArray) licObj.get("seeAlso"));
        Licenses.put(license.identifier, license);

        try (InputStream ltis = License.class.getResourceAsStream("/license-details/" + license.identifier + ".json")) {
          JSONObject textObj = (JSONObject) parser.parse(new InputStreamReader(ltis, StandardCharsets.UTF_8));
          license.text = (String) textObj.get("licenseText");
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    try (InputStream is = License.class.getResourceAsStream("/exceptions.json")) {
      JSONParser parser = new JSONParser();
      JSONObject root = (JSONObject) parser.parse(new InputStreamReader(is, StandardCharsets.UTF_8));
      JSONArray exceptionsArray = (JSONArray) root.get("exceptions");
      for (Object obj : exceptionsArray) {
        JSONObject exObj = (JSONObject) obj;
        LicenseTextException le = new LicenseTextException();
        le.detailsURL = (String) exObj.get("detailsUrl");
        le.identifier = (String) exObj.get("licenseExceptionId");
        le.name = (String) exObj.get("name");
        le.reference = (String) exObj.get("reference");
        Exceptions.put(le.identifier, le);

        try (InputStream ltis = License.class.getResourceAsStream("/license-exceptions/" + le.identifier + ".json")) {
          JSONObject textObj = (JSONObject) parser.parse(new InputStreamReader(ltis, StandardCharsets.UTF_8));
          le.text = (String) textObj.get("licenseExceptionText");
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

    Licenses.put("ApacheV1_0", Licenses.get("Apache-1.0"));
    Licenses.put("ApacheV1_1", Licenses.get("Apache-1.1"));
    Licenses.put("ApacheV2_0", Licenses.get("Apache-2.0"));
    Licenses.put("BSD", Licenses.get("0BSD"));
    Licenses.put("BSD_2_Clause", Licenses.get("BSD-2-Clause"));
    Licenses.put("BSD_3_Clause", Licenses.get("BSD-3-Clause"));
    Licenses.put("BSD_4_Clause", Licenses.get("BSD-4-Clause"));
    Licenses.put("CDDLV1_0", Licenses.get("CDDL-1.0"));
    Licenses.put("CDDLV1_1", Licenses.get("CDDL-1.1"));
    Licenses.put("EclipseV1_0", Licenses.get("EPL-1.0"));
    Licenses.put("GPLV1_0", Licenses.get("GPL-1.0-only"));
    Licenses.put("GPLV2_0", Licenses.get("GPL-2.0-only"));
    Licenses.put("GPLV2_0_CE", new License(Licenses.get("GPL-2.0-only"), Exceptions.get("classpath-exception-2.0"), null));
    Licenses.put("GPLV3_0", Licenses.get("GPL-3.0-only"));
    Licenses.put("LGPLV2_1", Licenses.get("LGPL-2.1-only"));
    Licenses.put("LGPLV3_0", Licenses.get("LGPL-3.0-only"));
    Licenses.put("Public_Domain", Licenses.get("NIST-PD"));

    CustomLicenses.add("Commercial");
    CustomLicenses.add("Other");
    CustomLicenses.add("OtherDistributableOpenSource");
    CustomLicenses.add("OtherNonDistributableOpenSource");
  }
}
