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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.lattejava.cli.domain.Project
import org.lattejava.io.ArchiveFileSet
import org.lattejava.io.Directory
import org.lattejava.io.FileSet
import org.lattejava.io.FileTools
import org.lattejava.io.tar.TarBuilder
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.runtime.BuildFailureException
import org.lattejava.security.Algorithm
import org.lattejava.security.Checksum

/**
 * Delegate for the deb method's closure. This does all the work of building debian package files.
 *
 * @author Brian Pontarelli
 */
class DebDelegate {
  public static final String DEBIAN_BINARY_FILE = "2.0\n"

  public static final String ERROR_MESSAGE = "The deb plugin build method must be called like this:\n\n" +
      "  deb.build(to: \"package.deb\") {\n" +
      "    tarFileSet(dir: \"some other dir\", prefix: \"some-prefix\")\n" +
      "  }"

  public final Project project

  public final Path to

  public String architecture

  public String conflicts

  public String depends

  public Description description

  public String enhances

  public String homepage

  public Maintainer maintainer

  public String pkg

  public Path postInst

  public Path postRm

  public String preDepends

  public Path preInst

  public Path preRm

  public String priority

  public String provides

  public String recommends

  public String replaces

  public String section

  public String suggests

  public Version version

  public List<ArchiveFileSet> files = []

  public List<ArchiveFileSet> conf = []

  public Set<Directory> directories = []

  DebDelegate(Map<String, Object> attributes, Project project) {
    this.project = project

    if (!GroovyTools.attributesValid(attributes,
        ["to", "architecture", "conflicts", "depends", "enhances", "homepage", "package", "postInst", "postRm", "preInst", "preDepends", "preRm",
         "priority", "provides", "recommends", "replaces", "section", "suggests"],
        ["to", "package", "section"],
        ["architecture": String.class, "conflicts": String.class, "depends": String.class, "enhances": String.class, "homepage": String.class,
         "package"     : String.class, "priority": String.class, "provides": String.class, "recommends": String.class, "replaces": String.class,
         "section"     : String.class, "suggests": String.class])) {
      throw new BuildFailureException(ERROR_MESSAGE);
    }

    this.to = FileTools.toPath(attributes["to"])
    this.architecture = attributes["architecture"]
    this.depends = attributes["depends"]
    this.priority = attributes["priority"]
    this.section = attributes["section"]
    this.pkg = attributes["package"]
    this.postInst = FileTools.toPath(attributes["postInst"])
    this.postRm = FileTools.toPath(attributes["postRm"])
    this.preDepends = FileTools.toPath(attributes["preDepends"])
    this.preInst = FileTools.toPath(attributes["preInst"])
    this.preRm = FileTools.toPath(attributes["preRm"])
    this.homepage = attributes["homepage"]
    this.replaces = attributes["replaces"]
    this.provides = attributes["provides"]
    this.conflicts = attributes["conflicts"]
    this.enhances = attributes["enhances"]
    this.suggests = attributes["suggests"]
    this.recommends = attributes["recommends"]

    // Set the default architecture
    if (!this.architecture) {
      this.architecture = "all"
    }

    if (section && !Section.isValid(section)) {
      throw new BuildFailureException("Invalid section [${section}] for the debian package")
    }

    if (priority && !Priority.isValid(priority)) {
      throw new BuildFailureException("Invalid section [${section}] for the debian package")
    }
  }

  void build() {
    if (!version || !version.debian || !version.upstream) {
      throw new BuildFailureException("Invalid Debian package information. The [version] is missing or incomplete");
    }
    if (!version.isDebianValid()) {
      throw new BuildFailureException("Invalid Debian package information. The [version.debian] is not a valid version string");
    }
    if (!version.isUpstreamValid()) {
      throw new BuildFailureException("Invalid Debian package information. The [version.upstream] is not a valid version string");
    }
    if (!maintainer || (!maintainer.name && !maintainer.email)) {
      throw new BuildFailureException("Invalid Debian package information. The [maintainer] is missing or incomplete");
    }
    if (!description || !description.extended || !description.synopsis) {
      throw new BuildFailureException("Invalid Debian package information. The [description] is missing or incomplete");
    }

    Path tempDir = Files.createTempDirectory("savant-deb-plugin")
    Path debianDir = tempDir.resolve("DEBIAN")
    Path md5File = debianDir.resolve("md5sums")
    if (files.size() > 0) {
      appendMD5s(md5File, files)
    }
    if (conf.size() > 0) {
      appendMD5s(md5File, conf)
    }

    if (conf.size() > 0) {
      Path confFile = debianDir.resolve("conffiles")
      createConfFiles(confFile, conf)
    }

    Path dataTar = tempDir.resolve("data.tar.gz")
    long installedSize = createDataFile(dataTar)

    Path controlFile = debianDir.resolve("control")
    createControlFile(controlFile, installedSize);

    Path controlTar = tempDir.resolve("control.tar.gz")
    createControlTar(controlTar, debianDir)

    Path debFile = project.directory.resolve(to).resolve("${pkg}_${version}_${architecture}.deb")
    if (Files.isRegularFile(debFile)) {
      Files.delete(debFile)
      Files.createFile(debFile)
    } else {
      Files.createDirectories(debFile.getParent())
      Files.createFile(debFile)
    }

    ArArchiveOutputStream aaos = new ArArchiveOutputStream(Files.newOutputStream(debFile))
    aaos.withStream { os ->
      os.putArchiveEntry(new ArArchiveEntry("debian-binary", DEBIAN_BINARY_FILE.length()))
      os.write(DEBIAN_BINARY_FILE.getBytes())
      os.closeArchiveEntry()

      os.putArchiveEntry(new ArArchiveEntry("control.tar.gz", Files.size(controlTar)))
      Files.copy(controlTar, os)
      os.closeArchiveEntry()

      os.putArchiveEntry(new ArArchiveEntry("data.tar.gz", Files.size(dataTar)))
      Files.copy(dataTar, os)
      os.closeArchiveEntry()
    }

    FileTools.prune(tempDir)
  }

  /**
   * Adds a confFileSet:
   * <p>
   * <pre>
   *   confFileSet(dir: "someDir", prefix: "some-prefix")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  void confFileSet(Map<String, Object> attributes) {
    String error = ArchiveFileSet.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    String userName = attributes["userName"] != null ? attributes["userName"] : "root"
    String groupName = attributes["groupName"] != null ? attributes["groupName"] : "root"
    Path dir = project.directory.resolve(FileTools.toPath(attributes.get("dir")))
    ArchiveFileSet fileSet = ArchiveFileSet.fromAttributes(dir, attributes)
        .withDirGroupName(groupName)
        .withDirUserName(userName)
    conf.add(fileSet)
  }

  /**
   * Sets the description of the package:
   * <p>
   * <pre>
   *   description(synopsis: "Cool package", extended: "Very cool package")
   * </pre>
   *
   * @param attributes The named attributes.
   */
  void description(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["synopsis", "extended"], [], ["synopsis": String.class, "extended": String.class])) {
      throw new BuildFailureException(ERROR_MESSAGE)
    }

    description = new Description(attributes["extended"], attributes["synopsis"])
  }

  /**
   * Adds a directory to the Debian package:
   * <p>
   * <pre>
   *   directory(name: "someDir", mode: 0x755)
   * </pre>
   *
   * @param attributes The named attributes (name is required).
   */
  void directory(Map<String, Object> attributes) {
    String error = Directory.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    Directory directory = Directory.fromAttributes(attributes)
    if (directory.userName == null) {
      directory.userName = "root"
    }
    if (directory.groupName == null) {
      directory.groupName = "root"
    }
    directories.add(directory)
  }

  /**
   * Sets the maintainer of the package:
   * <p>
   * <pre>
   *   maintainer(name: "Joe Smith", email: "joe@smith.com")
   * </pre>
   *
   * @param attributes The named attributes (name and email are required).
   */
  void maintainer(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["name", "email"], [], ["name": String.class, "email": String.class])) {
      throw new BuildFailureException(ERROR_MESSAGE)
    }

    maintainer = new Maintainer(attributes["name"], attributes["email"])
  }

  /**
   * Adds a TAR fileSet:
   * <p>
   * <pre>
   *   tarFileSet(dir: "someDir", prefix: "some-prefix")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  void tarFileSet(Map<String, Object> attributes) {
    String error = ArchiveFileSet.attributesValid(attributes)
    if (error != null) {
      throw new BuildFailureException(error)
    }

    String userName = attributes["userName"] != null ? attributes["userName"] : "root"
    String groupName = attributes["groupName"] != null ? attributes["groupName"] : "root"
    Path dir = project.directory.resolve(FileTools.toPath(attributes.get("dir")))
    ArchiveFileSet fileSet = ArchiveFileSet.fromAttributes(dir, attributes)
        .withDirGroupName(groupName)
        .withDirUserName(userName)
    files.add(fileSet)
  }

  /**
   * Sets the priority of the package:
   * <p>
   * <pre>
   *   priority(value: "required")
   * </pre>
   *
   * @param attributes The named attributes (value is required).
   */
  void version(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["upstream", "debian"], ["upstream"], ["upstream": String.class, "debian": String.class])) {
      throw new BuildFailureException(ERROR_MESSAGE)
    }

    version = new Version(attributes["upstream"], attributes["debian"])
  }

  private void appendMD5s(Path md5File, List<FileSet> files) {
    StringBuilder build = new StringBuilder()
    files.each { fileSet ->
      fileSet.toFileInfos().each { info ->
        build.append("${Checksum.md5(info.origin).sum} ${info.relative}\n")
      }
    }

    if (!Files.isDirectory(md5File.getParent())) {
      Files.createDirectories(md5File.getParent())
    }

    Files.write(md5File, build.toString().getBytes("UTF-8"), StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
  }

  private void createConfFiles(Path confFile, List<ArchiveFileSet> conf) {
    StringBuilder build = new StringBuilder()
    conf.each { fileSet ->
      fileSet.toFileInfos().each { info -> build.append(info.relative.toString()).append("\n") }
    }

    Files.write(confFile, build.toString().getBytes("UTF-8"))
  }

  private void createControlFile(Path controlFile, long installedSize) {
    StringBuilder build = new StringBuilder()
    build.append("Package: ${pkg}\n")
    build.append("Version: ${version}\n")

    if (section != null) {
      build.append("Section: ${section}\n")
    }

    if (priority != null) {
      build.append("Priority: ${priority}\n")
    }

    build.append("Architecture: ${architecture}\n")

    if (depends != null) {
      build.append("Depends: ${depends}\n")
    }

    if (preDepends != null) {
      build.append("Pre-Depends: ${preDepends}\n")
    }

    if (recommends != null) {
      build.append("Recommends: ${recommends}\n")
    }

    if (suggests != null) {
      build.append("Suggests: ${suggests}\n")
    }

    if (enhances != null) {
      build.append("Enhances: ${enhances}\n")
    }

    if (conflicts != null) {
      build.append("Conflicts: ${conflicts}\n")
    }

    if (provides != null) {
      build.append("Provides: ${provides}\n")
    }

    if (replaces != null) {
      build.append("Replaces: ${replaces}\n")
    }

    if (installedSize > 0) {
      build.append("Installed-Size: ${(long) (installedSize / 1024L)}\n")
    }

    build.append("Maintainer: ${maintainer}\n")

    if (homepage != null) {
      build.append("Homepage: ${homepage}\n")
    }

    build.append("Description: ${description.synopsis}\n${description.getExtendedFormatted()}\n")

    Files.write(controlFile, build.toString().getBytes("UTF-8"))
  }

  private long createDataFile(Path dataFile) {
    TarBuilder tarBuilder = new TarBuilder(dataFile)
    files.each { file -> tarBuilder.fileSet(file) }
    conf.each { conf -> tarBuilder.fileSet(conf) }
    directories.each { dir -> tarBuilder.directory(dir) }
    tarBuilder.build()
    return tarBuilder.getExplodedSize()
  }

  private void createControlTar(Path controlTar, Path debianDir) {
    if (postInst) {
      Files.copy(project.directory.resolve(postInst), debianDir.resolve("postinst"))
    }
    if (postRm) {
      Files.copy(project.directory.resolve(postRm), debianDir.resolve("postrm"))
    }
    if (preInst) {
      Files.copy(project.directory.resolve(preInst), debianDir.resolve("preinst"))
    }
    if (preRm) {
      Files.copy(project.directory.resolve(preRm), debianDir.resolve("prerm"))
    }

    new TarBuilder(controlTar)
        .fileSet(new ArchiveFileSet(debianDir, "", 0x644, null, null, null, null, null, [~/control/, ~/md5sums/, ~/conffiles/, ~/templates/, ~/triggers/], []))
        .fileSet(new ArchiveFileSet(debianDir, "", 0x755, null, null, null, null, null, [~/preinst/, ~/prerm/, ~/postinst/, ~/postrm/, ~/config/], []))
        .build()
  }
}
