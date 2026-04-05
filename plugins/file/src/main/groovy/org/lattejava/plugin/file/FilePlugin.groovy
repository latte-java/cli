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
package org.lattejava.plugin.file

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.io.jar.JarTools
import org.lattejava.io.tar.TarTools
import org.lattejava.io.zip.ZipTools
import org.lattejava.output.Output
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeFailureException
import org.lattejava.cli.runtime.RuntimeConfiguration

/**
 * File plugin.
 *
 * @author Brian Pontarelli
 */
class FilePlugin extends BaseGroovyPlugin {

  FilePlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
  }

  /**
   * Appends one or more files to a file. The "to" attribute specifies the target file and the "files" attribute is an
   * array of files to append to it.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.append(to: "build/somefile.txt") {
   *     fileSet(dir: "foo")
   *   }
   * </pre>
   *
   * @param attributes The named attributes (to and files are required).
   */
  void append(Map<String, Object> attributes, @DelegatesTo(AppendDelegate.class) Closure closure) {
    AppendDelegate delegate = new AppendDelegate(project, attributes)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()

    int count = delegate.append()
    output.infoln("Appended [%d] files to [%s]", count, attributes["to"])
  }

  /**
   * Copies files around. This uses the {@link CopyDelegate} class to handle Closure methods.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.copy(to: "build/distributions/bin") {
   *     fileSet(dir: "src/main/scripts")
   *   }
   * </pre>
   *
   * @param attributes The named attributes (to is required).
   * @param closure The closure that is invoked.
   * @return The number of files copied.
   */
  int copy(Map<String, Object> attributes, @DelegatesTo(CopyDelegate.class) Closure closure) {
    def delegate = new CopyDelegate(project, attributes)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    try {
      closure()

      int count = delegate.copier.copy()
      output.infoln("Copied [%d] files to [%s]", count, delegate.copier.to)
      return count
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
      return 0
    }
  }

  /**
   * Copies a single file to another location.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.copyFile(file: "some-file.txt", to: "build/some-file-renamed.txt")
   * </pre>
   *
   * @param attributes The named attributes (file and to are required).
   */
  void copyFile(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["to", "file"], ["to", "file"], [:])) {
      fail("You must supply the [file] and [to] attributes like this:\n\n" +
          "  file.copyFile(file: \"some-file.txt\", to: \"build/some-file-renamed.txt\")")
    }

    Path source = project.directory.resolve(FileTools.toPath(attributes["file"]))
    Path target = project.directory.resolve(FileTools.toPath(attributes["to"]))
    Files.createDirectories(target.getParent())
    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
  }

  /**
   * Deletes files using fileSets.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.delete {
   *     fileSet(dir: "build/example", includePatterns: [~/foobar.+/])
   *   }
   * </pre>
   */
  void delete(@DelegatesTo(DeleteDelegate.class) Closure closure) {
    DeleteDelegate delegate = new DeleteDelegate(project)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()

    int count = delegate.delete()
    output.infoln("Deleted [%d] files", count)
  }

  /**
   * Creates a JAR file from various files. This uses the {@link JarDelegate} class to handle Closure methods.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.jar(file: "build/jars/foo.jar") {
   *     fileSet(dir: "build/classes/main")
   *   }
   * </pre>
   *
   * @param attributes The named attributes (file is required).
   * @param closure The closure that is invoked.
   * @return The number of files added to the Jar.
   */
  int jar(Map<String, Object> attributes, @DelegatesTo(JarDelegate.class) Closure closure) {
    def delegate = new JarDelegate(project, attributes)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    try {
      closure()

      delegate.builder.ensureManifest("${project.group}.${project.name}".toString(), project.version.toString())

      int count = delegate.builder.build()
      output.infoln("Added [%d] files to JAR [%s]", count, delegate.builder.file)
      return count
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
      return 0
    }
  }

  /**
   * Creates a directory. Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.mkdir(dir: "foo")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  void mkdir(Map<String, Object> attributes) {
    Files.createDirectories(project.directory.resolve(FileTools.toPath(attributes["dir"])))
  }

  /**
   * Prunes the given directory. This does not traverse symlinks. It unlinks symlinks.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.prune(dir: "build/classes/main")
   * </pre>
   *
   * @param attributes The named attributes (dir is required).
   */
  void prune(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["dir"], ["dir"], [:])) {
      throw new RuntimeFailureException("The file plugin prune method must be called like this:\n\n" +
          "  file.prune(dir: \"some dir\")")
    }

    Path directory = FileTools.toPath(attributes["dir"])
    try {
      FileTools.prune(project.directory.resolve(directory))
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
    }
  }

  /**
   * Renames the files specified by one or more fileSets using the filter specified.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.rename {
   *     fileSet(dir: "build/classes/main")
   *     filter(token: "foobar", value: "baz")
   *   }
   * </pre>
   *
   * @param closure The closure
   */
  void rename(@DelegatesTo(RenameDelegate.class) Closure closure) {
    RenameDelegate renameDelegate = new RenameDelegate(project)
    closure.delegate = renameDelegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    closure()

    try {
      int count = renameDelegate.rename()
      output.infoln("Renamed [%d] files", count)
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
    }
  }

  /**
   * Creates a symbol link at the [link] attribute that points to the [target] attribute.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.symlink(target: "build/classes/main", link: "/tmp/foo/bar")
   * </pre>
   *
   * @param attributes The named attributes (target and link are required).
   */
  void symlink(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["target", "link"], ["target", "link"], [:])) {
      fail("The file plugin symlink method must be called like this:\n\n" +
          "  file.symlink(target: \"some dir\", link: \"some other dir\")")
    }

    Path target = project.directory.resolve(FileTools.toPath(attributes["target"])).toAbsolutePath()
    if (Files.notExists(target)) {
      fail("Invalid target directory [${target}]")
    }

    Path link = project.directory.resolve(FileTools.toPath(attributes["link"]))
    if (Files.exists(link) && Files.isSymbolicLink(link)) {
      Files.delete(link)
    } else if (Files.exists(link)) {
      fail("The link [${link}] exists and is a regular file or directory")
    } else if (Files.notExists(link.getParent())) {
      Files.createDirectories(link.getParent())
    }

    Files.createSymbolicLink(link, target)
  }

  /**
   * Creates a Tarball from various files. This uses the {@link TarDelegate} class to handle Closure methods.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.tar(file: "build/tars/foobar.tar.gz") {
   *     fileSet(dir: "src/main/java")
   *   }
   * </pre>
   *
   * @param attributes The named attributes (file is required).
   * @param closure The closure that is invoked.
   * @return The number of files added to the Tarball.
   */
  int tar(Map<String, Object> attributes, @DelegatesTo(TarDelegate.class) Closure closure) {
    def delegate = new TarDelegate(project, attributes)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    try {
      closure()

      int count = delegate.builder.build()
      output.infoln("Added [%d] files to TAR [%s]", count, delegate.builder.file)
      return count
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
      return 0
    }
  }

  /**
   * Unzips a JAR file to a directory. This requires the [file] and [to] attributes.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.unjar(file: "build/zips/foobar.jar", to: "build/output")
   * </pre>
   *
   * @param attributes The named attributes (file is required).
   */
  void unjar(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["file", "to"], ["file", "to"], [:])) {
      fail("You must supply a [file] and [to] attribute like this:\n" +
          "  file.unjar(file: \"foo.jar\", to: \"some-dir\")")
    }

    Path file = project.directory.resolve(FileTools.toPath(attributes["file"]))
    Path to = project.directory.resolve(FileTools.toPath(attributes["to"]))

    if (!Files.isRegularFile(file)) {
      fail("Zip file [${file}] does not exist")
    }

    try {
      output.infoln("Unjarring [${file}] to [${to}]")
      JarTools.unjar(file, to)
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
    }
  }

  /**
   * Untars a TAR file to a directory. This requires the [file] and [to] attributes.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.untar(file: "build/tars/foobar.tar.gz", to: "build/output")
   * </pre>
   *
   * @param attributes The named attributes (file is required).
   */
  void untar(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["file", "to", "useGroup", "useOwner"], ["file", "to"], [:])) {
      fail("You must supply a [file] and [to] attribute like this:\n" +
          "  file.unzip(file: \"foo.zip\", to: \"some-dir\")")
    }

    Path file = project.directory.resolve(FileTools.toPath(attributes["file"]))
    Path to = project.directory.resolve(FileTools.toPath(attributes["to"]))
    boolean useGroup = attributes["useGroup"] ? attributes["useGroup"] : false
    boolean useOwner = attributes["useOwner"] ? attributes["useOwner"] : false

    if (!Files.isRegularFile(file)) {
      fail("TAR file [${file}] does not exist")
    }

    try {
      output.infoln("Untarring [${file}] to [${to}]")
      TarTools.untar(file, to, useGroup, useOwner)
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
    }
  }

  /**
   * Unzips a ZIP file to a directory. This requires the [file] and [to] attributes.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.unzip(file: "build/zips/foobar.zip", to: "build/output")
   * </pre>
   *
   * @param attributes The named attributes (file is required).
   */
  void unzip(Map<String, Object> attributes) {
    if (!GroovyTools.attributesValid(attributes, ["file", "to"], ["file", "to"], [:])) {
      fail("You must supply a [file] and [to] attribute like this:\n" +
          "  file.unzip(file: \"foo.zip\", to: \"some-dir\")")
    }

    Path file = project.directory.resolve(FileTools.toPath(attributes["file"]))
    Path to = project.directory.resolve(FileTools.toPath(attributes["to"]))

    if (!Files.isRegularFile(file)) {
      fail("Zip file [${file}] does not exist")
    }

    try {
      output.infoln("Unzipping [${file}] to [${to}]")
      ZipTools.unzip(file, to)
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
    }
  }

  /**
   * Creates a ZIP file from various files. This uses the {@link ZipDelegate} class to handle Closure methods.
   * <p>
   * Here is an example of calling this method:
   * <p>
   * <pre>
   *   file.zip(file: "build/jars/foo.zip") {
   *     fileSet(dir: "build/classes/main")
   *   }
   * </pre>
   *
   * @param attributes The named attributes (file is required).
   * @param closure The closure that is invoked.
   * @return The number of files added to the ZIP.
   */
  int zip(Map<String, Object> attributes, @DelegatesTo(ZipDelegate.class) Closure closure) {
    def delegate = new ZipDelegate(project, attributes)
    closure.delegate = delegate
    closure.resolveStrategy = Closure.DELEGATE_FIRST
    try {
      closure()

      int count = delegate.builder.build()
      output.infoln("Added [%d] files to ZIP [%s]", count, delegate.builder.file)
      return count
    } catch (IOException e) {
      output.debug(e)
      fail(e.getMessage())
      return 0
    }
  }
}
