/*
 * Copyright (c) 2014-2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.database

import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
import org.lattejava.cli.runtime.RuntimeFailureException
import org.lattejava.dep.domain.License
import org.lattejava.domain.Version
import org.lattejava.output.Output
import org.lattejava.output.SystemOutOutput
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertTrue
import static org.testng.Assert.fail

/**
 * Tests the database plugin.
 *
 * @author Brian Pontarelli
 */
class DatabasePluginTest {
  public static Path projectDir

  Output output

  Project project

  @BeforeSuite
  static void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("project.latte"))) {
      projectDir = Paths.get("../database")
    }
  }

  @BeforeMethod
  void beforeMethod() {
    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir, output)
    project.group = "org.lattejava.test"
    project.name = "database"
    project.version = new Version("1.0.0")
    project.licenses.add(License.parse("ApacheV2_0", null))
  }

  @Test
  void mysqlEnsureEqual() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "mysql"
    plugin.settings.createUsername = "root"
    plugin.createTestDatabase()
    plugin.execute(file: "src/test/resources/test-mysql.sql")

    plugin.createMainDatabase()
    plugin.execute(file: "src/test/resources/test-mysql.sql")

    plugin.ensureEqual(left: "database", right: "database_test")
  }

  @Test
  void mysqlEnsureEqualFailsWhenDifferent() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "mysql"
    plugin.settings.createUsername = "root"
    plugin.createMainDatabase()
    plugin.execute(file: "src/test/resources/test-mysql.sql")

    plugin.createTestDatabase()
    plugin.execute(file: "src/test/resources/test-mysql-different.sql")

    try {
      plugin.ensureEqual(left: "database", right: "database_test")
      fail("Should have failed because the databases are different")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("not equal"), "Message was [${e.message}]")
      assertTrue(e.message.contains("name"), "Message was [${e.message}]")
    }
  }

  @Test
  void mysqlMigrate() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "mysql"
    plugin.settings.name = "database_migrate"
    plugin.settings.createUsername = "root"
    plugin.createDatabase()

    plugin.settings.migrationsDirectory = "src/test/resources/migrations-mysql"
    def applied = plugin.migrate()
    assertEquals(applied.collect { it.toString() }, ["1.0.0", "1.0.1"])

    Process process = ["mysql", "-udev", "-h127.0.0.1", "-pdev", "-e", "show tables", "-v", "database_migrate"].execute()
    assertEquals(process.text, "--------------\nshow tables\n--------------\n\nTables_in_database_migrate\nusers\nversions\n")
    assertEquals((long) process.exitValue(), 0)

    process = ["mysql", "-udev", "-h127.0.0.1", "-pdev", "-N", "-e", "SELECT version FROM versions ORDER BY version", "database_migrate"].execute()
    assertEquals(process.text, "1.0.0\n1.0.1\n")
    assertEquals((long) process.exitValue(), 0)

    process = ["mysql", "-udev", "-h127.0.0.1", "-pdev", "-N", "-e", "SELECT column_name FROM information_schema.columns WHERE table_schema = 'database_migrate' AND table_name = 'users' ORDER BY ordinal_position", "database_migrate"].execute()
    assertEquals(process.text, "id\nname\nemail\n")
    assertEquals((long) process.exitValue(), 0)

    assertTrue(plugin.migrate().isEmpty())
  }

  @Test
  void postgresqlMigrate() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.name = "database_migrate"
    plugin.settings.createUsername = "postgres"
    plugin.createDatabase()

    plugin.settings.migrationsDirectory = "src/test/resources/migrations-postgresql"
    plugin.settings.migrationTable = "schema_versions"
    def applied = plugin.migrate()
    assertEquals(applied.collect { it.toString() }, ["1.0.0", "1.0.1"])

    Process process = ["psql", "-Udev", "-h127.0.0.1", "-tA", "-c",
                       "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                       "database_migrate"].execute()
    assertEquals(process.text, "schema_versions\nusers\n")
    assertEquals((long) process.exitValue(), 0)

    process = ["psql", "-Udev", "-h127.0.0.1", "-tA", "-c",
               "SELECT version FROM schema_versions ORDER BY version", "database_migrate"].execute()
    assertEquals(process.text, "1.0.0\n1.0.1\n")
    assertEquals((long) process.exitValue(), 0)

    assertTrue(plugin.migrate().isEmpty())
  }

  @Test
  void migrateFailsWhenTypeNotDefined() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.migrate()
      fail("Should have failed because the database type is not defined")
    } catch (RuntimeFailureException e) {
      assertTypeNotDefinedMessage(e)
    }
  }

  @Test
  void migrateFailsWhenDirectoryMissing() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    try {
      plugin.migrate()
      fail("Should have failed because the default migrations directory does not exist")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("migrationsDirectory"), "Message was [${e.message}]")
    }
  }

  @Test
  void migrateFailsWithDatabaseErrorWhenMigrationInvalid() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.name = "database_migrate"
    plugin.settings.createUsername = "postgres"
    plugin.createDatabase()

    plugin.settings.migrationsDirectory = "src/test/resources/migrations-invalid"
    try {
      plugin.migrate()
      fail("Should have failed because the migration SQL is invalid")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("Migration [1.0.0] failed"), "Message was [${e.message}]")
      assertTrue(e.message.contains("Database error is ["), "Message was [${e.message}]")
      assertTrue(e.message.contains("syntax"), "Message was [${e.message}]")
    }
  }

  @Test
  void postgresqlEnsureEqual() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.createUsername = "postgres"
    plugin.createTestDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql.sql")

    plugin.createMainDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql.sql")

    plugin.ensureEqual(left: "database", right: "database_test")
  }

  @Test
  void mysqlDatabase() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "mysql"
    plugin.settings.createUsername = "root"
    plugin.createDatabase()

    plugin.execute(file: "src/test/resources/test-mysql.sql")

    Process process = ["mysql", "-udev", "-h127.0.0.1", "-pdev", "-e", "show tables", "-v", "database_plugin"].execute()
    assertEquals(process.text, "--------------\nshow tables\n--------------\n\nTables_in_database_plugin\ntest\n")
    assertEquals((long) process.exitValue(), 0)
  }

  @Test
  void mysqlCreateMainDatabase() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.name = "old"
    plugin.settings.type = "mysql"
    plugin.settings.createUsername = "root"
    plugin.createMainDatabase()

    plugin.execute(file: "src/test/resources/test-mysql.sql")

    Process process = ["mysql", "-udev", "-h127.0.0.1", "-pdev", "-e", "show tables", "-v", "database_plugin"].execute()
    assertEquals(process.text, "--------------\nshow tables\n--------------\n\nTables_in_database_plugin\ntest\n")
    assertEquals((long) process.exitValue(), 0)
  }

  @Test
  void mysqlCreateTestDatabase() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "mysql"
    plugin.settings.createUsername = "root"
    plugin.createTestDatabase()

    plugin.execute(file: "src/test/resources/test-mysql.sql")

    Process process = ["mysql", "-udev", "-h127.0.0.1", "-pdev", "-e", "show tables", "-v", "database_plugin_test"].execute()
    assertEquals(process.text, "--------------\nshow tables\n--------------\n\nTables_in_database_plugin_test\ntest\n")
    assertEquals((long) process.exitValue(), 0)
  }

  @Test
  void compareFailsWhenTypeNotDefined() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.compare(left: "database_plugin", right: "database_plugin_test")
      fail("Should have failed because the database type is not defined")
    } catch (RuntimeFailureException e) {
      assertTypeNotDefinedMessage(e)
    }
  }

  @Test
  void compareFailsWhenTypeUnsupported() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "oracle"
    try {
      plugin.compare(left: "database", right: "database_test")
      fail("Should have failed because the database type is unsupported")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("Unsupported database type [oracle]"), "Message was [${e.message}]")
    }
  }

  @Test
  void createDatabaseFailsWhenTypeNotDefined() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.createDatabase()
      fail("Should have failed because the database type is not defined")
    } catch (RuntimeFailureException e) {
      assertTypeNotDefinedMessage(e)
    }
  }

  @Test
  void executeFailsWhenTypeNotDefined() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    try {
      plugin.execute(file: "src/test/resources/test-mysql.sql")
      fail("Should have failed because the database type is not defined")
    } catch (RuntimeFailureException e) {
      assertTypeNotDefinedMessage(e)
    }
  }

  @Test
  void postgresqlEnsureEqualIgnoresColumnOrder() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.createUsername = "postgres"
    plugin.createMainDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql-column-order-left.sql")

    plugin.createTestDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql-column-order-right.sql")

    plugin.ensureEqual(left: "database", right: "database_test")
  }

  @Test
  void postgresqlCompareReturnsDifferences() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.createUsername = "postgres"
    plugin.createMainDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql.sql")

    plugin.createTestDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql-different.sql")

    DatabaseComparison comparison = plugin.compare(left: "database", right: "database_test")
    assertFalse(comparison.areEqual())
    assertTrue(comparison.differences.size() > 0, "Differences were [${comparison.differences}]")
  }

  @Test
  void postgresqlEnsureEqualFailsWhenDifferent() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.createUsername = "postgres"
    plugin.createMainDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql.sql")

    plugin.createTestDatabase()
    plugin.execute(file: "src/test/resources/test-postgresql-different.sql")

    try {
      plugin.ensureEqual(left: "database", right: "database_test")
      fail("Should have failed because the databases are different")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("not equal"), "Message was [${e.message}]")
      assertTrue(e.message.contains("name"), "Message was [${e.message}]")
    }
  }

  @Test
  void compareFailsWhenDatabaseUnreachable() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    try {
      plugin.compare(left: "latte_missing_database", right: "database")
      fail("Should have failed because the database does not exist")
    } catch (RuntimeFailureException e) {
      assertTrue(e.message.contains("[latte_missing_database]"), "Message was [${e.message}]")
      assertTrue(e.message.contains("[127.0.0.1]"), "Message was [${e.message}]")
    }
  }

  @Test
  void postgresqlDatabase() throws Exception {
    DatabasePlugin plugin = new DatabasePlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.type = "postgresql"
    plugin.settings.createUsername = "postgres"
    plugin.createDatabase()

    plugin.execute(file: "src/test/resources/test-postgresql.sql")

    Process process = ["psql", "-Udev", "-h127.0.0.1", "-c", "\\dt", "database_plugin"].execute()
    assertEquals(process.text.trim(), """List of tables
 Schema | Name | Type  | Owner 
--------+------+-------+-------
 public | test | table | dev
(1 row)""".trim())
    assertEquals((long) process.exitValue(), 0)
  }

  private static void assertTypeNotDefinedMessage(RuntimeFailureException e) {
    assertTrue(e.message.contains("database.settings.type = \"mysql\""), "Message was [${e.message}]")
    assertTrue(e.message.contains("database.settings.type = \"postgresql\""), "Message was [${e.message}]")
  }
}
