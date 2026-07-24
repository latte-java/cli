# jOOQ Database Comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Liquibase with jOOQ as the engine behind the database plugin's `compare()` and `ensureEqual()` methods, returning a plugin-owned `DatabaseComparison` result.

**Architecture:** Each database is snapshotted via jOOQ's `Meta` API over a plugin-managed JDBC connection, normalized to a schema-less DDL script (so differently-named databases compare by content), re-parsed, and diffed with `Meta.migrateTo()`. The resulting DDL statements are the differences: empty means equal.

**Tech Stack:** Groovy 5.0, Java 25, jOOQ 3.21.6 (OSS), TestNG, Latte build (`latte` command).

**Spec:** `docs/superpowers/specs/2026-07-23-jooq-database-comparison-design.md`

## Global Constraints

- Work happens on the `feat/jooq` branch (already created). Never commit to `main`.
- Commit messages follow Conventional Commits (`feat:`, `fix:`, `test:`, …) and end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- All test runs: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte test --test=DatabasePluginTest`. **Run with the sandbox disabled** — the tests exec `mysql`/`psql` and open sockets, which fail with EPERM under the sandbox.
- Tests require live local MySQL and PostgreSQL servers (both are running on this machine; `root`/`postgres` admin users, `dev`/`dev` compare credentials).
- New source files start with the SPDX header, no blank line above, before `package`:
  ```groovy
  /*
   * Copyright (c) 2026 The Latte Project
   * SPDX-License-Identifier: MIT
   */
  ```
- 2-space indent, 4-space continuation indent, 120-char target line length.
- Runtime values in error messages are wrapped in square brackets: `[value]`.
- jOOQ version is exactly `org.jooq:jooq:3.21.6`; plugin version becomes `0.5.0`.
- Alphabetize imports within groups; third-party group before `java.*` group (match the existing file).

---

### Task 1: Point the ensureEqual tests at the databases they create

The existing `mysqlEnsureEqual` and `postgresqlEnsureEqual` tests create databases named `database`/`database_test` (derived from `project.name`) but then compare stale leftover databases named `database_plugin`/`database_plugin_test`. Fix them to compare what they create. This must go green while still on Liquibase, so the rest of the plan has honest regression coverage.

**Files:**
- Modify: `plugins/database/src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy`

**Interfaces:**
- Consumes: existing `DatabasePlugin.ensureEqual(Map)` (Liquibase-backed at this point).
- Produces: `mysqlEnsureEqual` and `postgresqlEnsureEqual` comparing `left: "database", right: "database_test"` — later tasks rely on these exercising the real created schemas.

- [ ] **Step 1: Change the compared database names**

In `mysqlEnsureEqual`, replace:

```groovy
    plugin.ensureEqual(left: "database_plugin", right: "database_plugin_test")
```

with:

```groovy
    plugin.ensureEqual(left: "database", right: "database_test")
```

Make the identical replacement in `postgresqlEnsureEqual`. Do not touch `postgresqlEnsureEqualIgnoresColumnOrder` (it already compares `database`/`database_test`) or the `mysqlDatabase`/`mysqlCreate*` tests (their stale-name asserts are out of scope).

- [ ] **Step 2: Run the test class to verify everything passes**

Run: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte test --test=DatabasePluginTest` (sandbox disabled)
Expected: `Total tests run: 10, Passes: 10, Failures: 0, Skips: 0`

If either changed test fails here, STOP — that means Liquibase comparison of the freshly-created databases finds real differences, and the spec's assumption is wrong. Report back instead of patching.

- [ ] **Step 3: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/cli
git add plugins/database/src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy
git commit -m "test: Compare the databases the ensureEqual tests create instead of stale leftovers

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Swap the comparison engine to jOOQ

The whole engine swap is one task because `compare()` and `ensureEqual()` both sit on the Liquibase `DiffResult` type — the plugin cannot compile half-migrated. Two failing tests first, then the implementation, then everything green.

**Files:**
- Create: `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabaseComparison.groovy`
- Create: `plugins/database/src/test/resources/test-postgresql-different.sql`
- Modify: `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabasePlugin.groovy`
- Modify: `plugins/database/project.latte`
- Test: `plugins/database/src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy`

**Interfaces:**
- Consumes: `settings.type`, `settings.host`, `settings.compareUsername`, `settings.comparePassword`; `ensureTypeDefined()`; `fail(String, Object...)` from `BaseGroovyPlugin`.
- Produces: `DatabaseComparison compare(Map<String, Object> attributes)` where `DatabaseComparison` has `List<String> differences` and `boolean areEqual()`; `void ensureEqual(Map<String, Object> attributes)` failing with the DDL report; private `Connection makeConnection(String databaseName)` and `Meta snapshotMeta(String databaseName)` (Task 3 wraps `makeConnection` errors).

- [ ] **Step 1: Add the fixture for an unequal database**

Create `plugins/database/src/test/resources/test-postgresql-different.sql`:

```sql
create table test (
  id serial,
  name varchar(20)
)
```

(The base `test-postgresql.sql` creates `test` with only `id`, so the diff is one added column, `name`.)

- [ ] **Step 2: Write the two failing tests**

Add to `DatabasePluginTest.groovy`, after `postgresqlEnsureEqualIgnoresColumnOrder`:

```groovy
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
```

Add the needed static import alongside the existing ones (`assertEquals`, `assertTrue`, `fail`):

```groovy
import static org.testng.Assert.assertFalse
```

- [ ] **Step 3: Run to verify the new tests fail for the right reason**

Run: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte test --test=DatabasePluginTest` (sandbox disabled)
Expected: build FAILS to compile the test — `DatabaseComparison` does not exist yet (`unable to resolve class DatabaseComparison`). That is the correct failure. Anything else (e.g. tests run and pass) means the test is not exercising the new API — stop and fix the test.

- [ ] **Step 4: Create the DatabaseComparison result class**

Create `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabaseComparison.groovy`:

```groovy
/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
package org.lattejava.plugin.database

/**
 * The result of comparing two databases. The differences are the DDL statements that would transform the left database
 * into the right database. An empty list of differences means the databases are equal.
 *
 * @author Brian Pontarelli
 */
class DatabaseComparison {
  List<String> differences = []

  boolean areEqual() {
    return differences.empty
  }
}
```

- [ ] **Step 5: Swap the dependency and bump the plugin version**

In `plugins/database/project.latte`:
- Change the project line's version from `0.4.1` to `0.5.0`:
  ```groovy
  project(group: "org.lattejava.plugin", name: "database", version: "0.5.0", licenses: ["MIT"]) {
  ```
- In the `compile` dependency group, replace `dependency(id: "org.liquibase:liquibase-core:5.0.3")` with (keep the group alphabetized):
  ```groovy
  dependency(id: "org.jooq:jooq:3.21.6")
  ```
  Final compile group order: `com.mysql:mysql-connector-j:9.6.0`, `org.jooq:jooq:3.21.6`, `org.postgresql:postgresql:42.7.10`.

- [ ] **Step 6: Rewrite DatabasePlugin comparison code on jOOQ**

In `DatabasePlugin.groovy`:

**6a.** Replace the entire import block above `/**` class Javadoc with:

```groovy
import com.mysql.cj.jdbc.MysqlDataSource
import org.jooq.DSLContext
import org.jooq.Meta
import org.jooq.Queries
import org.jooq.SQLDialect
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
```

(All `liquibase.*` imports are gone. `org.jooq.conf.Settings` must not be star-imported — the plugin has its own `DatabaseSettings`, keep them distinct.)

**6b.** Replace `compare()` (Javadoc and method) with:

```groovy
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
```

**6c.** Replace `ensureEqual()` (Javadoc and method) with:

```groovy
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
```

(`fail("%s", message)` rather than `fail(message)` — the base class formats the message with `String.format`, and DDL can contain `%` characters.)

**6d.** Delete `makeLiquibaseDatabase()` entirely and add these two private methods in its place (before `execAndWait`):

```groovy
  private Connection makeConnection(String databaseName) {
    if (settings.type.toLowerCase() == "mysql") {
      MysqlDataSource ds = new MysqlDataSource()
      ds.setURL("jdbc:mysql://${settings.host}:3306/${databaseName}?serverTimezone=UTC&useSSL=false")
      return ds.getConnection(settings.compareUsername, settings.comparePassword)
    }

    PGSimpleDataSource ds = new PGSimpleDataSource()
    ds.setUrl("jdbc:postgresql://${settings.host}:5432/${databaseName}")
    ds.setUser(settings.compareUsername)
    ds.setPassword(settings.comparePassword)
    return ds.getConnection()
  }

  /**
   * Snapshots the schema of the given database as a schema-less jOOQ Meta so that databases with different names can
   * be compared by content. The snapshot is rendered to DDL without schema qualification and re-parsed.
   */
  private Meta snapshotMeta(String databaseName) {
    boolean mysql = settings.type.toLowerCase() == "mysql"
    String schemaName = mysql ? databaseName : "public"
    SQLDialect dialect = mysql ? SQLDialect.MYSQL : SQLDialect.POSTGRES

    Connection connection = makeConnection(databaseName)
    try {
      DSLContext context = DSL.using(connection, new Settings().withRenderSchema(false))
      String ddl = context.meta().filterSchemas { it.name == schemaName }.snapshot().ddl().toString()
      return DSL.using(dialect).meta(ddl)
    } finally {
      connection.close()
    }
  }
```

- [ ] **Step 7: Run the test class to verify everything passes**

Run: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte test --test=DatabasePluginTest` (sandbox disabled)
Expected: `Total tests run: 12, Passes: 12, Failures: 0, Skips: 0`

The critical canaries: `postgresqlEnsureEqualIgnoresColumnOrder` (jOOQ must not flag column order), `mysqlEnsureEqual` (schema-name normalization must make `database` vs `database_test` comparable). If jOOQ flags column order, the normalization DDL round-trip is the place to fix it (column order in rendered DDL follows the snapshot; investigate before patching, report if non-obvious).

- [ ] **Step 8: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/cli
git add plugins/database
git commit -m "feat!: Switch database comparison from Liquibase to jOOQ

compare() now returns a plugin-owned DatabaseComparison instead of a
Liquibase DiffResult, manages its own connections, and honors
settings.host. ensureEqual() reports differences as the DDL statements
that would make the left database match the right one.

BREAKING CHANGE: compare() returns DatabaseComparison; callers must use
areEqual()/differences and no longer close connections.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Fail helpfully when a database is unreachable

**Files:**
- Modify: `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabasePlugin.groovy`
- Test: `plugins/database/src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy`

**Interfaces:**
- Consumes: `makeConnection(String)` and `snapshotMeta(String)` from Task 2; `RuntimeFailureException` (already imported in the test).
- Produces: `snapshotMeta` fails the build with `Unable to connect to database [name] on host [host]. Error is [message]` on `SQLException`.

- [ ] **Step 1: Write the failing test**

Add to `DatabasePluginTest.groovy` after `postgresqlEnsureEqualFailsWhenDifferent`:

```groovy
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
```

- [ ] **Step 2: Run to verify it fails for the right reason**

Run: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte test --test=DatabasePluginTest` (sandbox disabled)
Expected: `compareFailsWhenDatabaseUnreachable` FAILS with a raw `org.postgresql.util.PSQLException` (database "latte_missing_database" does not exist) — not a `RuntimeFailureException`. All other tests pass.

- [ ] **Step 3: Wrap the connection in a helpful failure**

In `DatabasePlugin.groovy`, add `java.sql.SQLException` to the `java.sql` import group:

```groovy
import java.sql.Connection
import java.sql.SQLException
```

Then in `snapshotMeta`, replace the line `Connection connection = makeConnection(databaseName)` with:

```groovy
    Connection connection
    try {
      connection = makeConnection(databaseName)
    } catch (SQLException e) {
      fail("Unable to connect to database [%s] on host [%s]. Error is [%s]", databaseName, settings.host, e.message)
    }
```

- [ ] **Step 4: Run the test class to verify everything passes**

Run: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte test --test=DatabasePluginTest` (sandbox disabled)
Expected: `Total tests run: 13, Passes: 13, Failures: 0, Skips: 0`

- [ ] **Step 5: Run the full plugin build as final verification**

Run: `cd /Users/bpontarelli/dev/latte-java/cli/plugins/database && latte clean test` (sandbox disabled)
Expected: build succeeds, `Total tests run: 13, Passes: 13, Failures: 0, Skips: 0`

- [ ] **Step 6: Commit**

```bash
cd /Users/bpontarelli/dev/latte-java/cli
git add plugins/database
git commit -m "feat: Fail the build with a helpful message when a compared database is unreachable

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
