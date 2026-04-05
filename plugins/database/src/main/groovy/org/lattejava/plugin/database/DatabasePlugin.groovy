/*
 * Copyright (c) 2014-2018, Inversoft Inc., All Rights Reserved
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
package org.lattejava.plugin.database

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

import org.postgresql.jdbc2.optional.SimpleDataSource
import org.lattejava.cli.domain.Project
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration

import com.mysql.cj.jdbc.MysqlDataSource
import liquibase.Liquibase
import liquibase.changelog.DatabaseChangeLog
import liquibase.configuration.GlobalConfiguration
import liquibase.configuration.LiquibaseConfiguration
import liquibase.database.Database
import liquibase.database.core.MySQLDatabase
import liquibase.database.core.PostgresDatabase
import liquibase.database.jvm.JdbcConnection
import liquibase.diff.DiffResult
import liquibase.diff.compare.CompareControl
import liquibase.diff.output.report.DiffToReport
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.structure.core.Column
import liquibase.structure.core.Data
import liquibase.structure.core.ForeignKey
import liquibase.structure.core.Index
import liquibase.structure.core.PrimaryKey
import liquibase.structure.core.Schema
import liquibase.structure.core.Sequence
import liquibase.structure.core.StoredProcedure
import liquibase.structure.core.Table
import liquibase.structure.core.UniqueConstraint
import liquibase.structure.core.View

/**
 * Database plugin.
 *
 * @author Brian Pontarelli
 */
class DatabasePlugin extends BaseGroovyPlugin {
  DatabaseSettings settings

  DatabasePlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    settings = new DatabaseSettings(project)
  }

  /**
   * Compares two databases using Liquibase. This takes two attributes that specify the databases to compare: right and
   * left. Here's how to call this method:
   * <p>
   * <pre>
   *   database.settings.type = "mysql"
   *   database.settings.compareUsername = "dev"
   *   database.settings.comparePassword = "dev"
   *   def result = database.compare(left: "database1", right: "database2)
   *   result.areEqual()
   *   result.getReferenceSnapshot().getDatabase().close()
   *   result.getComparisonSnapshot().getDatabase().close()
   * </pre>
   * <p>
   * <strong>NOTE:</strong> Callers must take steps to close the database connections inside the DiffResult object.
   *
   * @param attributes The named attributes (left and right are required).
   * @return The Liquibase DiffResult.
   */
  DiffResult compare(Map<String, Object> attributes) {
    if (!GroovyTools.hasAttributes(attributes, "left", "right")) {
      fail("You must specify the names of the databases to compare like this:\n\n" +
          "  database.compare(left: \"database1\", right: \"database2\")")
    }

    String leftDatabaseName = attributes["left"].toString()
    String rightDatabaseName = attributes["right"].toString()
    output.infoln("Comparing database [${leftDatabaseName}] to [${rightDatabaseName}]")
    Database leftDatabase = makeLiquibaseDatabase(leftDatabaseName)
    Database rightDatabase = makeLiquibaseDatabase(rightDatabaseName)

    DatabaseChangeLog databaseChangeLog = new DatabaseChangeLog()
    Liquibase liquibase = new Liquibase(databaseChangeLog, new ClassLoaderResourceAccessor(), leftDatabase)
    CompareControl compareControl = new CompareControl([Column.class, Data.class, ForeignKey.class, Index.class, PrimaryKey.class, Schema.class, Sequence.class, StoredProcedure.class, Table.class, UniqueConstraint.class, View.class] as Set)

    // We don't want to diff column order (because we don't order postgres columns and it shouldn't matter that much anyways)
    LiquibaseConfiguration.getInstance().getConfiguration(GlobalConfiguration.class).setDiffColumnOrder(false)

    return liquibase.diff(leftDatabase, rightDatabase, compareControl)
  }

  /**
   * Runs a comparison between two databases and fails if they are not equal. This takes two attributes that specify the
   * databases to compare: right and left. Here's how to call this method:
   * <p>
   * <pre>
   *   database.settings.type = "mysql"
   *   database.settings.compareUsername = "dev"
   *   database.settings.comparePassword = "dev"
   *   database.ensureEqual(left: "database1", right: "database2)
   * </pre>
   *
   * @param attributes The named attributes (left and right are required).
   */
  void ensureEqual(Map<String, Object> attributes) {
    DiffResult result = compare(attributes)
    try {
      if (!result.areEqual()) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        new DiffToReport(result, new PrintStream(baos)).print()
        fail("Database are not equal. Errors are:\n\n[%s]", baos.toString("UTF-8"))
      }
    } finally {
      result.getReferenceSnapshot().getDatabase().close()
      result.getComparisonSnapshot().getDatabase().close()
    }
  }

  /**
   * Creates a database using the {@link #settings}. Here is an example of calling this plugin method:
   * <p>
   * <pre>
   *   database.settings.name = "foo_bar"
   *   database.settings.type = "mysql"
   *   database.settings.createUsername = "root"
   *   database.createDatabase()
   * </pre>
   */
  void createDatabase() {
    output.infoln("Creating database [${settings.name}]")

    if (settings.type.toLowerCase() == "mysql") {
      String createUsername = (settings.createUsername) ? settings.createUsername : "root"
      execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "DROP DATABASE IF EXISTS ${settings.name}"])
      execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "CREATE DATABASE ${settings.name} ${settings.createSuffix}"])

      if (settings.grantUsername) {
        output.infoln("Granting privileges to [${settings.grantUsername}]")
        execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "CREATE USER '${settings.grantUsername}'@'%' IDENTIFIED BY '${settings.grantPassword}'"], true)
        execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "GRANT ALL PRIVILEGES ON ${settings.name}.* TO '${settings.grantUsername}'@'%'"])
      }
    } else if (settings.type.toLowerCase() == "postgresql") {
      String createUsername = (settings.createUsername) ? settings.createUsername : "postgres"
      execAndWait(["psql", "-U${createUsername}", "-h${settings.host}", settings.createArguments, "-c", "DROP DATABASE IF EXISTS ${settings.name}"])
      execAndWait(["psql", "-U${createUsername}", "-h${settings.host}", settings.createArguments, "-c", "CREATE DATABASE ${settings.name} ${settings.createSuffix}"])

      if (settings.grantUsername) {
        output.infoln("Granting privileges to [${settings.grantUsername}]")
        execAndWait(["psql", "-U${createUsername}", "-h${settings.host}", settings.createArguments, "-c", "CREATE ROLE ${settings.grantUsername} login superuser password '${settings.grantPassword}'"], true)
        execAndWait(["psql", "-U${createUsername}", "-h${settings.host}", settings.createArguments, "-c", "GRANT ALL PRIVILEGES ON DATABASE ${settings.name} TO ${settings.grantUsername}"])
        execAndWait(["psql", "-U${createUsername}", "-h${settings.host}", settings.createArguments, "-c", "ALTER DATABASE ${settings.name} OWNER TO ${settings.grantUsername}"])
      }
    } else {
      fail("Unsupported database type [${settings.type}]")
    }
  }

  /**
   * Creates a database based off the project name. This replaces - and . with _ in the project name.
   */
  void createMainDatabase() {
    settings.name = project.name.replaceAll("-", "_").replaceAll("\\.", "_")
    createDatabase()
  }

  /**
   * Creates a test database based off the project name. This replaces - and . with _ in the project name. It then
   * appends _test to the end.
   */
  void createTestDatabase() {
    settings.name = project.name.replaceAll("-", "_").replaceAll("\\.", "_") + "_test"
    createDatabase()
  }

  /**
   * Executes the file specified by the {@code file} attribute. Here is an example of calling this method:
   * <pre>
   *   database.settings.name = "foo-bar"
   *   database.settings.type = "mysql"
   *   database.settings.grantUsername = "root"
   *   database.execute(file: "foo.sql")
   * </pre>
   */
  void execute(Map<String, Object> attributes) {
    if (!GroovyTools.hasAttributes(attributes, "file")) {
      fail("You must specify the name of the SQL file to execute using the file attribute like this:\n\n  database.execute(file: \"foo.sql\")")
    }

    output.infoln("Executing SQL script [${attributes["file"]}]")

    Path file = FileTools.toPath(attributes["file"])
    Path resolvedFile = project.directory.resolve(file)
    if (!Files.isRegularFile(resolvedFile) || !Files.isReadable(resolvedFile)) {
      fail("Invalid SQL script to execute [${resolvedFile}]")
    }

    String script = new String(Files.readAllBytes(resolvedFile), "UTF-8")
    if (settings.type.toLowerCase() == "mysql") {
      execAndWait(["mysql", "-u${settings.executeUsername}", "-h${settings.host}", "-p${settings.executePassword}", "-v", settings.executeArguments, settings.name], script, attributes['file'].toString())
    } else if (settings.type.toLowerCase() == "postgresql") {
      execAndWait(["psql", "-U${settings.executeUsername}", "-h${settings.host}", settings.executeArguments, settings.name], script, attributes['file'].toString())
    } else {
      fail("Unsupported database type [${settings.type}]")
    }
  }

  private Database makeLiquibaseDatabase(String name) {
    Database database
    if (settings.type == "mysql") {
      MysqlDataSource ds = new MysqlDataSource()
      ds.setURL("jdbc:mysql://localhost:3306/${name}?serverTimezone=UTC&useSSL=false")
      Connection c = ds.getConnection(settings.compareUsername, settings.comparePassword)
      database = new MySQLDatabase()
      database.setConnection(new JdbcConnection(c))
    } else {
      SimpleDataSource ds = new SimpleDataSource()
      ds.setUrl("jdbc:postgresql://localhost:5432/${name}")
      ds.setUser(settings.compareUsername)
      ds.setPassword(settings.comparePassword)
      database = new PostgresDatabase()
      database.setConnection(new JdbcConnection(ds.getConnection()))
    }
    return database
  }

  private void execAndWait(List<String> command, boolean ignoreFailure = false) {
    command.removeAll { it.trim().isEmpty() }

    output.debugln("Running [%s]", command.join(" "))

    Process process = command.execute()
    StringBuilder out = new StringBuilder()
    StringBuilder err = new StringBuilder()
    process.consumeProcessOutput(out, err)

    int code = process.waitFor()
    output.debugln(out.toString())
    output.debugln(err.toString())
    if (code != 0 && !ignoreFailure) {
      fail("Command [${command.join(' ')}] failed. Turn on debugging to see the error message from the database.")
    }
  }

  private void execAndWait(List<String> command, String input, String fileName) {
    command.removeAll { it.trim().isEmpty() }

    output.debugln("Running [%s]", command.join(" ") + " < ${fileName}")

    Process process = command.execute()
    StringBuilder out = new StringBuilder()
    StringBuilder err = new StringBuilder()
    process.consumeProcessOutput(out, err)
    process.withWriter { writer ->
      writer << input
    }

    int code = process.waitFor()
    output.debugln(out.toString())
    output.debugln(err.toString())
    if (code != 0) {
      fail("Command [${command.join(' ')} < ${fileName}] failed. Turn on debugging to see the error message from the database.")
    }
  }
}
