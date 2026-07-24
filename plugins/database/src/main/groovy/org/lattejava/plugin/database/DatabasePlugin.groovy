/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.database

import com.mysql.cj.jdbc.MysqlDataSource
import org.jooq.*
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.lattejava.cli.domain.Project
import org.lattejava.cli.parser.groovy.GroovyTools
import org.lattejava.cli.plugin.groovy.BaseGroovyPlugin
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.io.FileTools
import org.lattejava.output.Output
import org.postgresql.ds.PGSimpleDataSource

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException

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
   * Compares two databases using jOOQ. This takes two attributes that specify the databases to compare: right and
   * left. Here's how to call this method:
   * <p>
   * <pre>
   *   database.settings.type = "mysql"
   *   database.settings.compareUsername = "dev"
   *   database.settings.comparePassword = "dev"
   *   def result = database.compare(left: "database1", right: "database2")
   *   result.areEqual()
   *   result.differences.each { println it }
   * </pre>
   *
   * @param attributes The named attributes (left and right are required).
   * @return The DatabaseComparison whose differences are the DDL statements that would transform the left database
   *     into the right database.
   */
  DatabaseComparison compare(Map<String, Object> attributes) {
    ensureTypeDefined()

    if (!GroovyTools.hasAttributes(attributes, "left", "right")) {
      fail("You must specify the names of the databases to compare like this:\n\n" +
          "  database.compare(left: \"database1\", right: \"database2\")")
    }

    String leftDatabaseName = attributes["left"].toString()
    String rightDatabaseName = attributes["right"].toString()
    output.infoln("Comparing database [${leftDatabaseName}] to [${rightDatabaseName}]")

    Meta leftMeta = snapshotMeta(leftDatabaseName)
    Meta rightMeta = snapshotMeta(rightDatabaseName)
    Queries queries = leftMeta.migrateTo(rightMeta)
    return new DatabaseComparison(differences: queries.queries().collect { it.toString() })
  }

  /**
   * Runs a comparison between two databases and fails if they are not equal. This takes two attributes that specify the
   * databases to compare: right and left. Here's how to call this method:
   * <p>
   * <pre>
   *   database.settings.type = "mysql"
   *   database.settings.compareUsername = "dev"
   *   database.settings.comparePassword = "dev"
   *   database.ensureEqual(left: "database1", right: "database2")
   * </pre>
   *
   * @param attributes The named attributes (left and right are required).
   */
  void ensureEqual(Map<String, Object> attributes) {
    DatabaseComparison comparison = compare(attributes)
    if (!comparison.areEqual()) {
      fail("%s", "Databases [${attributes['left']}] and [${attributes['right']}] are not equal. " +
          "To make [${attributes['left']}] match [${attributes['right']}] you would run:\n\n" +
          comparison.differences.collect { "  " + it }.join("\n"))
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
    ensureTypeDefined()

    output.infoln("Creating database [${settings.name}]")

    if (settings.type.toLowerCase() == "mysql") {
      String createUsername = (settings.createUsername) ? settings.createUsername : "root"
      execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "DROP DATABASE IF EXISTS `${settings.name}`"])
      execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "CREATE DATABASE `${settings.name}` ${settings.createSuffix}"])

      if (settings.grantUsername) {
        output.infoln("Granting privileges to [${settings.grantUsername}]")
        execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "CREATE USER '${settings.grantUsername}'@'%' IDENTIFIED BY '${settings.grantPassword}'"], true)
        execAndWait(["mysql", "-u${createUsername}", "-h${settings.host}", "-v", settings.createArguments, "-e", "GRANT ALL PRIVILEGES ON `${settings.name}`.* TO '${settings.grantUsername}'@'%'"])
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
    ensureTypeDefined()

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

  /**
   * Fails the build if the database type has not been defined in the settings.
   */
  private void ensureTypeDefined() {
    if (!settings.type) {
      fail("You must specify the database type in the settings before calling the database plugin like this:\n\n" +
          "  database.settings.type = \"mysql\"\n\n" +
          "or\n\n" +
          "  database.settings.type = \"postgresql\"")
    }
  }

  private Connection makeConnection(String databaseName) {
    if (settings.type.toLowerCase() == "mysql") {
      MysqlDataSource ds = new MysqlDataSource()
      ds.setURL("jdbc:mysql://${settings.host}:3306/${databaseName}?serverTimezone=UTC&useSSL=false")
      return ds.getConnection(settings.compareUsername, settings.comparePassword)
    } else if (settings.type.toLowerCase() == "postgresql") {
      PGSimpleDataSource ds = new PGSimpleDataSource()
      ds.setUrl("jdbc:postgresql://${settings.host}:5432/${databaseName}")
      ds.setUser(settings.compareUsername)
      ds.setPassword(settings.comparePassword)
      return ds.getConnection()
    }

    fail("Unsupported database type [${settings.type}]")
    return null // Not possible but static analysis is dumb
  }

  /**
   * Snapshots the schema of the given database as a schema-less jOOQ Meta so that databases with different names can
   * be compared by content. The snapshot is rendered to DDL without schema qualification and re-parsed.
   * <p>
   * MySQL treats each database as its own schema, so {@code database} and {@code database_test} would otherwise
   * render as differently-named schemas (e.g. {@code create schema `database`}) and always compare as unequal.
   * {@link DDLFlag#SCHEMA} is left out of the export so the schema-creation statement itself is never rendered,
   * normalizing the schema name away entirely.
   */
  private Meta snapshotMeta(String databaseName) {
    boolean mysql = settings.type.toLowerCase() == "mysql"
    String schemaName = mysql ? databaseName : "public"
    SQLDialect dialect = mysql ? SQLDialect.MYSQL : SQLDialect.POSTGRES

    try (Connection connection = makeConnection(databaseName)) {
      DSLContext context = DSL.using(connection, new Settings().withRenderSchema(false))
      DDLExportConfiguration ddlConfig = new DDLExportConfiguration()
          .flags(DDLFlag.values().findAll { it != DDLFlag.SCHEMA } as DDLFlag[])
      String ddl = context.meta().filterSchemas { it.name == schemaName }.snapshot().ddl(ddlConfig).toString()
      return DSL.using(dialect).meta(ddl)
    } catch (SQLException e) {
      fail("Unable to connect to database [%s] on host [%s]. Error is [%s]", databaseName, settings.host, e.message)
      return null // Not possible but static analysis is dumb
    }
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
