/*
 * Copyright (c) 2024, Inversoft Inc., All Rights Reserved
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
package org.lattejava.dep;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lattejava.dep.domain.Artifact;
import org.lattejava.dep.domain.ArtifactID;
import org.lattejava.dep.domain.ArtifactMetaData;
import org.lattejava.dep.domain.ArtifactSpec;
import org.lattejava.dep.domain.Dependencies;
import org.lattejava.dep.domain.DependencyGroup;
import org.lattejava.dep.domain.License;
import org.lattejava.dep.domain.json.AMDJson;
import org.lattejava.dep.domain.json.AMDJsonDependency;
import org.lattejava.dep.domain.json.AMDJsonLicense;
import org.lattejava.domain.Version;
import org.lattejava.domain.VersionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is a toolkit for handling artifact operations.
 *
 * @author Brian Pontarelli
 */
public class ArtifactTools {
  /**
   * Maven version error.
   */
  public static final String VersionError = """
      Invalid Version in the dependency graph from a Maven dependency [%s]. You must specify a semantic version mapping for Savant to properly handle Maven dependencies. This goes at the top-level of the build file and looks like this:

      project(...) {
        workflow {
          semanticVersions {
            mapping(id: "org.badver:badver:1.0.0.Final", version: "1.0.0")
          }
        }
      }""";

  /**
   * Determines the semantic version of an artifact based on the original version from the specification, which might be
   * a Maven version.
   *
   * @param spec     The specification.
   * @param mappings The version mappings from non-semantic to semantic.
   * @return The version and never null.
   * @throws VersionException If the version is non-semantic and there is no mapping.
   */
  public static Version determineSemanticVersion(ArtifactSpec spec, Map<String, Version> mappings)
      throws VersionException {
    Version version = mappings.get(spec.mavenSpec);
    if (version != null) {
      return version; // Always favor a mapping
    }

    String originalVersion = spec.version;
    try {
      return new Version(originalVersion);
    } catch (VersionException e) {
      // If the version is janky (i.e. it contains random characters), throw an exception
      if (originalVersion.chars().anyMatch(ch -> !Character.isDigit(ch) && ch != '.')) {
        throw new VersionException(String.format(VersionError, spec.mavenSpec));
      }

      // Otherwise, try again by "fixing" the Maven version
      int dots = (int) originalVersion.chars().filter(ch -> ch == '.').count();
      if (dots == 0) {
        originalVersion += ".0.0";
      } else if (dots == 1) {
        originalVersion += ".0";
      }

      try {
        return new Version(originalVersion);
      } catch (VersionException e2) {
        throw new VersionException(String.format(VersionError, spec.mavenSpec));
      }
    }
  }

  /**
   * Parses the MetaData from the given JSON .amd.json file.
   *
   * @param file     The file to read the JSON MetaData information from.
   * @param mappings The semantic version mappings used when the JSON contains non-semantic versions.
   * @return The MetaData parsed.
   * @throws IOException If the parse operation failed because of an IO error.
   * @throws VersionException If any of the version strings could not be parsed.
   */
  public static ArtifactMetaData parseArtifactMetaDataJSON(Path file, Map<String, Version> mappings)
      throws IOException, VersionException {
    ObjectMapper objectMapper = new ObjectMapper();
    AMDJson amdJson = objectMapper.readValue(file.toFile(), AMDJson.class);

    // Convert licenses
    List<License> licenses = new ArrayList<>();
    if (amdJson.licenses != null) {
      for (AMDJsonLicense jsonLicense : amdJson.licenses) {
        licenses.add(License.parse(jsonLicense.type, jsonLicense.text));
      }
    }

    // Convert dependency groups
    Dependencies dependencies = null;
    if (amdJson.dependencyGroups != null) {
      dependencies = new Dependencies();
      for (Map.Entry<String, List<AMDJsonDependency>> entry : amdJson.dependencyGroups.entrySet()) {
        DependencyGroup group = new DependencyGroup(entry.getKey(), true);
        for (AMDJsonDependency jsonDep : entry.getValue()) {
          ArtifactSpec spec = new ArtifactSpec(jsonDep.id);
          List<ArtifactID> exclusions = new ArrayList<>();
          if (jsonDep.exclusions != null) {
            for (String exclusionSpec : jsonDep.exclusions) {
              exclusions.add(new ArtifactID(exclusionSpec));
            }
          }

          Version version;
          String nonSemanticVersion = null;
          if (!isStrictSemanticVersion(spec.version)) {
            nonSemanticVersion = spec.version;
            version = determineSemanticVersion(spec, mappings);
          } else {
            try {
              version = new Version(spec.version);
            } catch (VersionException e) {
              nonSemanticVersion = spec.version;
              version = determineSemanticVersion(spec, mappings);
            }
          }

          group.dependencies.add(new Artifact(spec.id, version, nonSemanticVersion, exclusions));
        }
        dependencies.groups.put(entry.getKey(), group);
      }
    }

    return new ArtifactMetaData(dependencies, licenses);
  }

  /**
   * Generates a temporary file that contains ArtifactMetaData JSON.
   *
   * @param artifactMetaData The MetaData object to serialize to JSON.
   * @return The temp file and never null.
   * @throws IOException If the temp could not be created or the JSON could not be written.
   */
  public static Path generateJSON(ArtifactMetaData artifactMetaData) throws IOException {
    File tmp = File.createTempFile("latte", "amd.json");
    tmp.deleteOnExit();

    List<AMDJsonLicense> licenses = new ArrayList<>();
    for (License license : artifactMetaData.licenses) {
      licenses.add(new AMDJsonLicense(license.identifier, license.customText ? license.text : null));
    }

    Map<String, List<AMDJsonDependency>> dependencyGroups = null;
    Dependencies dependencies = artifactMetaData.dependencies;
    if (dependencies != null) {
      dependencyGroups = new java.util.LinkedHashMap<>();
      for (Map.Entry<String, DependencyGroup> entry : dependencies.groups.entrySet()) {
        DependencyGroup group = entry.getValue();
        if (!group.export) {
          continue;
        }

        List<AMDJsonDependency> jsonDeps = new ArrayList<>();
        for (Artifact artifact : group.dependencies) {
          String version = artifact.nonSemanticVersion != null ? artifact.nonSemanticVersion : artifact.version.toString();
          String id = artifact.id.group + ":" + artifact.id.project + ":" + artifact.id.name + ":" + version + ":" + artifact.id.type;
          List<String> exclusions = null;
          if (!artifact.exclusions.isEmpty()) {
            exclusions = new ArrayList<>();
            for (ArtifactID exclusion : artifact.exclusions) {
              exclusions.add(exclusion.group + ":" + exclusion.project + ":" + exclusion.name + ":" + exclusion.type);
            }
          }
          jsonDeps.add(new AMDJsonDependency(id, exclusions));
        }
        dependencyGroups.put(entry.getKey(), jsonDeps);
      }
    }

    AMDJson amdJson = new AMDJson(licenses, dependencyGroups);
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp, amdJson);

    return tmp.toPath();
  }

  /**
   * Generates a temporary file that contains ArtifactMetaData XML which includes all the artifacts in the
   * ArtifactMetaData given.
   *
   * @param artifactMetaData The MetaData object to serialize to XML.
   * @return The temp file and never null.
   * @throws IOException If the temp could not be created or the XML could not be written.
   */
  public static Path generateXML(ArtifactMetaData artifactMetaData) throws IOException {
    File tmp = File.createTempFile("savant", "amd");
    tmp.deleteOnExit();

    try (PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
      pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      pw.printf("<artifact-meta-data>\n");

      artifactMetaData.licenses.forEach(license -> {
        if (license.text != null && license.customText) {
          print(pw, "  <license type=\"%s\"><![CDATA[%s]]></license>\n", license.identifier, license.text);
        } else {
          print(pw, "  <license type=\"%s\"/>\n", license.identifier);
        }
      });

      Dependencies dependencies = artifactMetaData.dependencies;
      if (dependencies != null) {
        pw.println("  <dependencies>");

        dependencies.groups.forEach((type, group) -> {
          if (!group.export) {
            return;
          }

          print(pw, "    <dependency-group name=\"%s\">\n", group.name);

          for (Artifact dependency : group.dependencies) {
            ArtifactID id = dependency.id;
            String version = dependency.nonSemanticVersion != null ? dependency.nonSemanticVersion : dependency.version.toString();
            print(pw, "      <dependency group=\"%s\" project=\"%s\" name=\"%s\" version=\"%s\" type=\"%s\">\n", id.group, id.project, id.name, version, id.type);

            if (!dependency.exclusions.isEmpty()) {
              for (ArtifactID exclusion : dependency.exclusions) {
                print(pw, "        <exclusion group=\"%s\" project=\"%s\" name=\"%s\" type=\"%s\"/>\n", exclusion.group, exclusion.project, exclusion.name, exclusion.type);
              }
            }

            pw.printf("      </dependency>\n");
          }

          pw.println("    </dependency-group>");
        });

        pw.println("  </dependencies>");
      }
      pw.println("</artifact-meta-data>");
    }

    return tmp.toPath();
  }

  /**
   * Parses the MetaData from the given Savant .amd file.
   *
   * @param file     The File to read the XML MetaData information from.
   * @param mappings The semantic version mappings that are used when the AMD is parsed and uses non-semantic versions
   *                 for transitive dependencies.
   * @return The MetaData parsed.
   * @throws SAXException If the SAX parsing failed.
   * @throws VersionException If any of the version strings could not be parsed.
   * @throws ParserConfigurationException If the parser configuration in the JDK is invalid.
   * @throws IOException If the parse operation failed because of an IO error.
   */
  public static ArtifactMetaData parseArtifactMetaData(Path file, Map<String, Version> mappings)
      throws SAXException, VersionException, ParserConfigurationException, IOException {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    ArtifactMetaDataHandler handler = new ArtifactMetaDataHandler(mappings);
    parser.parse(file.toFile(), handler);
    return new ArtifactMetaData(handler.dependencies, handler.licenses);
  }

  /**
   * Returns true if the version string is a strict semantic version (has at least major.minor.patch, i.e. at least 2
   * dots before any pre-release or metadata separator).
   *
   * @param version The version string to check.
   * @return True if the version has at least two dots before any - or + character.
   */
  private static boolean isStrictSemanticVersion(String version) {
    int dots = 0;
    for (int i = 0; i < version.length(); i++) {
      char c = version.charAt(i);
      if (c == '-' || c == '+') {
        break;
      }
      if (c == '.') {
        dots++;
      }
    }
    return dots >= 2;
  }

  private static void print(PrintWriter writer, String message, Object... args) {
    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].toString().replace("\"", "&quot;");
    }

    writer.printf(message, args);
  }

  public static class ArtifactMetaDataHandler extends DefaultHandler {
    public final List<ArtifactID> exclusions = new ArrayList<>();

    public final List<License> licenses = new ArrayList<>();

    private final Map<String, Version> mappings;

    public Dependencies dependencies;

    public DependencyGroup dependencyGroup;

    public ArtifactID dependencyId;

    public String dependencyNonSemanticVersion;

    public Version dependencyVersion;

    public String licenseId;

    public StringBuilder licenseText;

    public ArtifactMetaDataHandler(Map<String, Version> mappings) {
      this.mappings = mappings;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (licenseText != null) {
        licenseText.append(ch, start, length);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if (qName.equals("license")) {
        String text = licenseText.toString().trim();
        if (text.trim().isEmpty()) {
          text = null;
        }

        try {
          License license = License.parse(licenseId, text);
          licenses.add(license);
        } catch (IllegalArgumentException e) {
          throw new SAXException("Invalid AMD file. The license [" + licenseId + "] is not an allowed license type. Allowable values are " + License.Licenses.keySet() + " plus the custom license types of [Commercial, Other, OtherDistributableOpenSource, OtherNonDistributableOpenSource]", e);
        }

        licenseId = null;
        licenseText = null;
      } else if (qName.equals("dependency")) {
        dependencyGroup.dependencies.add(new Artifact(dependencyId, dependencyVersion, dependencyNonSemanticVersion, exclusions));
        dependencyId = null;
        dependencyVersion = null;
        exclusions.clear();
      }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      String name, group, project, type, version;
      switch (qName) {
        case "dependencies":
          dependencies = new Dependencies();
          break;
        case "dependency-group":
          name = attributes.getValue("name");
          if (name == null) {
            throw new SAXException("Invalid AMD file. The dependency-group elements must specify a [name] attribute");
          }

          dependencyGroup = new DependencyGroup(name, true);
          dependencies.groups.put(name, dependencyGroup);

          break;
        case "dependency":
          group = attributes.getValue("group");
          if (group == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [group] attribute");
          }

          project = attributes.getValue("project");
          if (project == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [project] attribute");
          }

          name = attributes.getValue("name");
          if (name == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [name] attribute");
          }

          type = attributes.getValue("type");
          if (type == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [type] attribute");
          }

          version = attributes.getValue("version");
          if (version == null) {
            throw new SAXException("Invalid AMD file. The dependency elements must specify a [version] attribute");
          }

          if (this.dependencyGroup == null) {
            throw new SAXException("Invalid AMD file. A dependency doesn't appear to be inside a dependency-group element");
          }

          dependencyId = new ArtifactID(group, project, name, type);
          try {
            dependencyVersion = new Version(version);
            dependencyNonSemanticVersion = null;
          } catch (VersionException e) {
            // Non-semantic version (e.g., "4.1.126.Final") — store the original version string
            // so fetch/publish can use the real version on disk and in remote repositories
            dependencyNonSemanticVersion = version;

            ArtifactSpec spec = new ArtifactSpec(group + ":" + project + ":" + name + ":" + version + ":" + type);
            dependencyVersion = ArtifactTools.determineSemanticVersion(spec, mappings);
          }

          break;
        case "exclusion":
          group = attributes.getValue("group");
          if (group == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [group] attribute");
          }
          project = attributes.getValue("project");
          if (project == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [project] attribute");
          }
          name = attributes.getValue("name");
          if (name == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [name] attribute");
          }
          type = attributes.getValue("type");
          if (type == null) {
            throw new SAXException("Invalid AMD file. The exclusion elements must specify a [type] attribute");
          }

          if (dependencyId == null || dependencyVersion == null) {
            throw new SAXException("Invalid AMD file. An exclusion doesn't appear to be inside a dependency element");
          }

          exclusions.add(new ArtifactID(group, project, name, type));
          break;
        case "license":
          licenseId = attributes.getValue("type");
          if (licenseId == null) {
            throw new SAXException("Invalid AMD file. The license elements must contain a [type] attribute");
          }

          licenseText = new StringBuilder();
          break;
        case "artifact-meta-data":
          if (attributes.getValue("license") != null) {
            throw new IllegalArgumentException("Invalid AMD file. It contains the old license definition on the <artifact-meta-data> element");
          }

          break;
        default:
          throw new SAXException("Invalid element encountered in AMD file [" + qName + "].");
      }
    }
  }
}
