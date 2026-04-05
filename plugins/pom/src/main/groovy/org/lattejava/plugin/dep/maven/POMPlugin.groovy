/*
 * Copyright (c) 2022-2025, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.dep.maven

import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.lattejava.cli.domain.Project
import org.lattejava.output.Output
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.w3c.dom.Document
import org.w3c.dom.Element

import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory

/**
 * Maven POM plugin.
 *
 * @author Brian Pontarelli
 */
class POMPlugin extends BaseGroovyPlugin {
  public POMSettings settings = new POMSettings()

  POMPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
  }

  /**
   * Updates the project's pom.xml file using its dependencies. The optional closure is invoked and passed the Groovy
   * {@link Node} instance for the root element of the pom.xml file.
   *
   * @param closure The closure.
   */
  void update(Closure closure = null) {
    Path pomFile = project.directory.resolve("pom.xml")
    if (Files.isDirectory(pomFile) || !Files.isReadable(pomFile) || !Files.isWritable(pomFile)) {
      fail("Maven pom.xml is not readable, writable, or is a directory (🤷)")
    }

    String text
    if (Files.exists(pomFile)) {
      output.infoln("Updating the project pom.xml file")
      byte[] bytes = Files.readAllBytes(pomFile)
      text = new String(bytes, StandardCharsets.UTF_8)
    } else {
      output.infoln("Creating the project pom.xml file")
      text = """<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
</project>"""
    }

    Document document = DOMBuilder.newInstance().parseText(text)
    Element root = document.documentElement

    // Remove the existing dependencies
    use(DOMCategory) {
      DOMCategory.setGlobalKeepIgnorableWhitespace(true)

      Element dependencies = root.dependencies.find()
      if (dependencies != null) {
        dependencies.children().each { dep -> dependencies.removeChild(dep) }
      }

      // Remove the existing licenses
      Element licenses = root.licenses.find()
      if (licenses != null) {
        licenses.children().each { lic -> licenses.removeChild(lic) }
      }

      // Add the dependencies
      updatePOM(root)

      // Call the closure if it exists
      if (closure) {
        closure.call(root)
      }

      // Write out the POM file
      StringWriter writer = new StringWriter()
      Transformer transformer = TransformerFactory.newInstance().newTransformer()
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
      transformer.setOutputProperty(OutputKeys.STANDALONE, "yes")
      transformer.transform(new DOMSource(document), new StreamResult(writer))

      String result = writer.toString()
      output.debugln("New pom.xml is\n\n%s", result)
      pomFile.toFile().write(result)

      output.infoln("Update complete")
    }
  }

  private void updatePOM(Element pom) {
    Element dependencies = pom.dependencies.find()
    if (dependencies == null) {
      dependencies = appendNode(pom, "dependencies", 2)
    }

    project.dependencies.groups.values().each { group ->
      group.dependencies.each { dep ->
        def options = settings.groupToScope[group.name]
        if (options == null) {
          options = ["scope": group.name, "optional": group.name.endsWith("-optional")]
        }

        Element dependency = appendNode(dependencies, "dependency", 4)
        setElement(dependency, "groupId", dep.id.group, 6)
        setElement(dependency, "artifactId", dep.id.name, 6)
        // When we write out the pom.xml, we want to use the maven version if it is different
        String version = dep.nonSemanticVersion != null
            ? dep.nonSemanticVersion
            : dep.version.toString();
        setElement(dependency, "version", version, 6)
        setElement(dependency, "type", dep.id.type, 6)
        setElement(dependency, "scope", (String) options.scope, 6)
        setElement(dependency, "optional", (String) options.optional, 6)

        if (dep.exclusions.size() > 0) {
          Element exclusions = appendNode(dependency, "exclusions", 6)
          dep.exclusions.each { exclude ->
            Element exclusion = appendNode(exclusions, "exclusion", 8)
            setElement(exclusion, "groupId", exclude.group, 10)
            setElement(exclusion, "artifactId", exclude.name, 10)
            closeNode(exclusion, 8)
          }

          closeNode(exclusions, 6)
        }

        closeNode(dependency, 4)
      }
    }

    closeNode(dependencies, 2)

    Element licenses = pom.licenses.find()
    if (licenses == null) {
      licenses = appendNode(pom, "licenses", 2)
    }

    project.licenses.each { lic ->
      Element license = appendNode(licenses, "license", 4)
      setElement(license, "name", lic.identifier, 6)
      setElement(license, "url", (lic.seeAlso != null && lic.seeAlso.size() > 0) ? lic.seeAlso.get(0) : null, 6)
      setElement(license, "distribution", "repo", 6)
      closeNode(license, 4)
    }

    closeNode(licenses, 2)

    setElement(pom, "groupId", project.group, 2)
    setElement(pom, "artifactId", project.name, 2)
    setElement(pom, "version", project.version.toString(), 2)
  }

  private static Element appendNode(Element element, String name, int indent) {
    StringBuilder build = new StringBuilder("\n")
    for (int i = 0; i < indent; i++) {
      build.append(" ")
    }

    element.appendChild(element.getOwnerDocument().createTextNode(build.toString()))
    return element.appendNode(name)
  }

  private static void closeNode(Element element, int indent) {
    StringBuilder build = new StringBuilder("\n")
    for (int i = 0; i < indent; i++) {
      build.append(" ")
    }

    element.appendChild(element.getOwnerDocument().createTextNode(build.toString()))
  }

  private static void setElement(Element root, String nodeName, String value, int indent) {
    if (value == null) {
      return
    }

    Element node = root.get(nodeName).find()
    if (node == null) {
      node = appendNode(root, nodeName, indent)
    }
    node.setValue(value)
  }
}
