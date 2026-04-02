/*
 * Copyright (c) 2022-2024, Inversoft Inc., All Rights Reserved
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.LicenseException;
import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactSpec;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.ReifiedArtifact;
import org.lattejava.dep.ArtifactTools;
import org.lattejava.domain.Version;
import org.lattejava.output.Output;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Maven helpers for things like parsing POMs.
 *
 * @author Brian Pontarelli
 */
public class MavenTools {
  /**
   * Parses a POM XML file.
   *
   * @param file   The file.
   * @param output The output in case the POM is borked.
   * @return The POM object and never null;
   * @throws POMException If the parsing failed.
   */
  public static POM parsePOM(Path file, Output output) throws POMException {
    POM pom = new POM();

    try {
      removeInvalidCharactersInPom(file, output);

      DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document d = b.parse(file.toFile());
      Element pomElement = d.getDocumentElement();
      pom.version = childText(pomElement, "version");
      pom.group = childText(pomElement, "groupId");
      pom.id = childText(pomElement, "artifactId");
      pom.name = childText(pomElement, "name");
      pom.packaging = childText(pomElement, "packaging");

      // Grab the parent info
      Element parentNode = firstChild(pomElement, "parent");
      if (parentNode != null) {
        pom.parentGroup = childText(parentNode, "groupId");
        pom.parentId = childText(parentNode, "artifactId");
        pom.parentVersion = childText(parentNode, "version");
      }

      // Grab the properties
      Element propertiesNode = firstChild(pomElement, "properties");
      if (propertiesNode != null) {
        NodeList propertyList = propertiesNode.getChildNodes();
        for (int i = 0; i < propertyList.getLength(); i++) {
          Node property = propertyList.item(i);
          if (property.getNodeType() == Node.ELEMENT_NODE) {
            String name = property.getNodeName();
            String value = property.getTextContent().trim();
            pom.properties.put(name, value);
          }
        }
      }

      // Grab the dependencies (top-level)
      Element dependenciesNode = firstChild(pomElement, "dependencies");
      if (dependenciesNode != null) {
        NodeList dependencyList = dependenciesNode.getChildNodes();
        for (int i = 0; i < dependencyList.getLength(); i++) {
          if (dependencyList.item(i).getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }

          pom.dependencies.add(parseDependency((Element) dependencyList.item(i)));
        }
      }

      // Grab the dependencyManagement info (top-level)
      Element depMgntNode = firstChild(pomElement, "dependencyManagement");
      if (depMgntNode != null) {
        Element depMgntDepsNode = firstChild(depMgntNode, "dependencies");
        if (depMgntDepsNode != null) {
          NodeList dependencyList = depMgntDepsNode.getChildNodes();
          for (int i = 0; i < dependencyList.getLength(); i++) {
            if (dependencyList.item(i).getNodeType() != Node.ELEMENT_NODE) {
              continue;
            }

            pom.dependenciesDefinitions.add(parseDependency((Element) dependencyList.item(i)));
          }
        }
      }

      // Grab the licenses
      Element licensesNode = firstChild(pomElement, "licenses");
      if (licensesNode != null) {
        NodeList licenseList = licensesNode.getChildNodes();
        for (int i = 0; i < licenseList.getLength(); i++) {
          if (licenseList.item(i).getNodeType() != Node.ELEMENT_NODE) {
            continue;
          }

          pom.licenses.add(parseLicense((Element) licenseList.item(i)));
        }
      }

      return pom;
    } catch (Exception e) {
      writeOutBadPom(file, output);
      throw new POMException("Unable to parse a Maven POM.", e);
    }
  }

  public static String replaceProperties(String value, Map<String, String> properties) {
    if (value == null) {
      return null;
    }

    for (String key : properties.keySet()) {
      String replacement = properties.get(key);
      if (replacement == null) {
        continue;
      }

      value = value.replace("${" + key + "}", replacement);
    }

    return value;
  }

  public static Artifact toArtifact(POM pom, String type, Map<String, Version> mappings) {
    ArtifactSpec spec = new ArtifactSpec(pom.toSpecification());
    Version version = ArtifactTools.determineSemanticVersion(spec, mappings);
    ArtifactID id = new ArtifactID(pom.group, pom.id, pom.id, type);
    return new Artifact(id, version, pom.version, null);
  }

  public static Dependencies toLatteDependencies(POM pom, Map<String, Version> mappings) {
    Dependencies dependencies = new Dependencies();
    pom.resolveAllDependencies().forEach(dep -> {
      String groupName = dep.scope;

      // Skip provided, test, system, and optional dependencies because Maven specifies that none of these dependencies
      // are transitive and none should exist in classpaths. In reality, Maven sucks so hard that there are actual POMs
      // in the wild that have circular dependencies and others that reference POMs that don't actually exist.
      if (groupName.equalsIgnoreCase("provided") || groupName.equalsIgnoreCase("test") || groupName.equalsIgnoreCase("system") ||
          (dep.optional != null && dep.optional.equalsIgnoreCase("true"))) {
        return;
      }

      DependencyGroup dependencyGroup = dependencies.groups.get(groupName);
      if (dependencyGroup == null) {
        dependencyGroup = new DependencyGroup(groupName, true);
        dependencies.groups.put(groupName, dependencyGroup);
      }

      List<ArtifactID> exclusions = new ArrayList<>();
      if (!dep.exclusions.isEmpty()) {
        for (MavenExclusion exclusion : dep.exclusions) {
          exclusions.add(new ArtifactID(exclusion.group, exclusion.id, exclusion.id, "*"));
        }
      }

      ArtifactSpec spec = new ArtifactSpec(dep.toSpecification());
      Version mapping = ArtifactTools.determineSemanticVersion(spec, mappings);
      ArtifactID id = new ArtifactID(dep.group, dep.id, dep.getArtifactName(), (dep.type == null ? "jar" : dep.type));
      dep.latteArtifact = new ReifiedArtifact(id, mapping, dep.version, exclusions, Collections.emptyList());
      dependencyGroup.dependencies.add(dep.latteArtifact);
    });

    return dependencies;
  }

  public static List<License> toLatteLicenses(POM pom) {
    List<License> licenses = new ArrayList<>();
    for (MavenLicense license : pom.licenses) {
      // Lookup by SPDX id
      License spdxLicense;
      try {
        spdxLicense = License.parse(license.name, null);
      } catch (LicenseException e) {
        // Try the URL now
        spdxLicense = License.lookupByURL(license.url);
        if (spdxLicense == null) {
          spdxLicense = License.parse("Other", license.name);
        }
      }

      licenses.add(spdxLicense);
    }

    return licenses;
  }

  private static String childText(Element root, String childName) {
    Element child = firstChild(root, childName);
    if (child != null) {
      return child.getTextContent();
    }

    return null;
  }

  private static Element firstChild(Element root, String childName) {
    NodeList childList = root.getChildNodes();
    for (int i = 0; i < childList.getLength(); i++) {
      Node childNode = childList.item(i);
      if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getNodeName().equals(childName)) {
        return (Element) childNode;
      }
    }

    return null;
  }

  private static MavenDependency parseDependency(Element dependencyNode) {
    MavenDependency artifact = new MavenDependency();
    artifact.group = childText(dependencyNode, "groupId");
    artifact.id = childText(dependencyNode, "artifactId");
    artifact.version = childText(dependencyNode, "version");
    artifact.classifier = childText(dependencyNode, "classifier");
    artifact.type = childText(dependencyNode, "type");
    artifact.optional = childText(dependencyNode, "optional");
    artifact.scope = childText(dependencyNode, "scope");

    Element exclusions = firstChild(dependencyNode, "exclusions");
    if (exclusions != null) {
      NodeList exclusionList = exclusions.getElementsByTagName("exclusion");
      for (int i = 0; i < exclusionList.getLength(); i++) {
        if (exclusionList.item(i).getNodeType() != Node.ELEMENT_NODE) {
          continue;
        }

        Element exclusion = (Element) exclusionList.item(i);
        String group = childText(exclusion, "groupId");
        String id = childText(exclusion, "artifactId");
        artifact.exclusions.add(new MavenExclusion(group, id));
      }
    }

    return artifact;
  }

  private static MavenLicense parseLicense(Element licenseNode) {
    MavenLicense license = new MavenLicense();
    license.distribution = childText(licenseNode, "distribution");
    license.name = childText(licenseNode, "name");
    license.url = childText(licenseNode, "url");
    return license;
  }

  private static void removeInvalidCharactersInPom(Path file, Output output) {
    try {
      String pomString = Files.readString(file);
      if (pomString.contains("&oslash;")) {
        output.warning("Found and replaced [&oslash;] with [O] to keep the parser from exploding.");
        Files.writeString(file, pomString.replace("&oslash;", "O"), StandardOpenOption.TRUNCATE_EXISTING);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void writeOutBadPom(Path file, Output output) {
    output.error("Bad POM, failed to parse. I copied it to /tmp/invalid_pom if you want to take a look and see what is fookered.");

    try {
      Files.copy(file, Paths.get("/tmp/invalid_pom"), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ignore) {
    }
  }
}
