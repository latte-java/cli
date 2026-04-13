/*
 * Copyright (c) 2014-2024, Inversoft Inc., All Rights Reserved
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

import org.lattejava.cli.domain.Project
import org.lattejava.cli.runtime.RuntimeConfiguration
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

    plugin.ensureEqual(left: "database_plugin", right: "database_plugin_test")
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

    plugin.ensureEqual(left: "database_plugin", right: "database_plugin_test")
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
}
