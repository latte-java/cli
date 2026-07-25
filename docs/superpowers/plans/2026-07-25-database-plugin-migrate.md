# Database Plugin `migrate()` Method Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a settings-driven `migrate()` method to the database plugin that applies SQL migrations via the Latte database library's `Migrator`.

**Architecture:** The plugin (`plugins/database`, Groovy) gains a new compile dependency on `org.lattejava:database:0.1.0`. `migrate()` opens a JDBC connection using the plugin's existing connection settings (execute credentials) and delegates to `org.lattejava.database.migration.Migrator`, which applies `<semver>.sql` files in SemVer order and records versions in a table. The existing `makeConnection` helper is generalized to accept credentials.

**Tech Stack:** Groovy 5.0, Java 25, TestNG, Latte build (`latte` CLI), live MySQL + PostgreSQL on 127.0.0.1 (user `dev`/`dev`, admin `root`/`postgres`).

**Spec:** `docs/superpowers/specs/2026-07-25-database-plugin-migrate-design.md`

## Global Constraints

- All work happens in `plugins/database/` (plus this plan/spec in `docs/`). The database library itself (`../database`) is NOT modified.
- The plugin is its own Latte project: run all `latte` commands from `plugins/database/`.
- Tests need live MySQL and PostgreSQL on 127.0.0.1 and shell out to `mysql`/`psql`; run `latte` test commands with the sandbox disabled (they fail with EPERM/connection errors otherwise).
- Commit messages must be Conventional Commits (`feat:`, `test:`, `refactor:`, …) — a `commit-msg` hook enforces this.
- New Groovy source files are not created; new SQL fixture files do not get license headers (matching `src/test/resources/test-mysql.sql`).
- The new dependency version is exactly `org.lattejava:database:0.1.0` (released; no `-{integration}` suffix).

---

### Task 1: Dependency and settings

**Files:**
- Modify: `plugins/database/project.latte` (compile dependency group, ~line 26)
- Modify: `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabaseSettings.groovy`

**Interfaces:**
- Consumes: nothing new.
- Produces: `settings.migrationsDirectory` (String, default `"src/main/resources/db"`) and `settings.migrationTable` (String, default `"versions"`), used by Task 3's `migrate()`. Makes `org.lattejava.database.migration.*` available on the plugin's compile classpath.

- [ ] **Step 1: Add the dependency to `project.latte`**

In the `compile` group (currently mysql-connector-j, jooq, postgresql), insert in group-id alphabetical order:

```groovy
    group(name: "compile") {
      dependency(id: "com.mysql:mysql-connector-j:9.6.0")
      dependency(id: "org.jooq:jooq:3.21.6")
      dependency(id: "org.lattejava:database:0.1.0")
      dependency(id: "org.postgresql:postgresql:42.7.10")
    }
```

- [ ] **Step 2: Add the two settings**

In `DatabaseSettings.groovy`, after the `host` field (keep the existing Javadoc comment style):

```groovy
  /**
   * The location of the SQL migration scripts, resolved against the project directory. Defaults to
   * {@code src/main/resources/db}.
   */
  String migrationsDirectory = "src/main/resources/db"

  /**
   * The name of the table that migrations record applied versions in. Defaults to {@code versions}.
   */
  String migrationTable = "versions"
```

- [ ] **Step 3: Verify the build resolves and compiles**

Run (from `plugins/database/`): `latte build`
Expected: succeeds; the new `org.lattejava:database:0.1.0` artifact is fetched/resolved without errors.

- [ ] **Step 4: Commit**

```bash
git add project.latte src/main/groovy/org/lattejava/plugin/database/DatabaseSettings.groovy
git commit -m "feat: Add database library dependency and migration settings to database plugin"
```

---

### Task 2: Generalize `makeConnection` to accept credentials

**Files:**
- Modify: `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabasePlugin.groovy:198-213` (`makeConnection`) and `:229` (call site in `snapshotMeta`)

**Interfaces:**
- Consumes: existing `settings.host`, `settings.type`.
- Produces: `private Connection makeConnection(String databaseName, String username, String password)` — used by `snapshotMeta` (compare credentials) and Task 3's `migrate()` (execute credentials).

- [ ] **Step 1: Change the signature and body**

Replace the current `makeConnection(String databaseName)` with:

```groovy
  private Connection makeConnection(String databaseName, String username, String password) {
    if (settings.type.toLowerCase() == "mysql") {
      MysqlDataSource ds = new MysqlDataSource()
      ds.setURL("jdbc:mysql://${settings.host}:3306/${databaseName}?serverTimezone=UTC&useSSL=false")
      return ds.getConnection(username, password)
    } else if (settings.type.toLowerCase() == "postgresql") {
      PGSimpleDataSource ds = new PGSimpleDataSource()
      ds.setUrl("jdbc:postgresql://${settings.host}:5432/${databaseName}")
      ds.setUser(username)
      ds.setPassword(password)
      return ds.getConnection()
    }

    fail("Unsupported database type [${settings.type}]")
    return null // Not possible but static analysis is dumb
  }
```

- [ ] **Step 2: Update the call site in `snapshotMeta`**

```groovy
    try (Connection connection = makeConnection(databaseName, settings.compareUsername, settings.comparePassword)) {
```

- [ ] **Step 3: Run the existing test suite to verify no regression**

Run (from `plugins/database/`, sandbox disabled): `latte test`
Expected: all existing `DatabasePluginTest` tests pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/groovy/org/lattejava/plugin/database/DatabasePlugin.groovy
git commit -m "refactor: Pass credentials into makeConnection in the database plugin"
```

---

### Task 3: `migrate()` — MySQL happy path drives the implementation

**Files:**
- Create: `plugins/database/src/test/resources/migrations-mysql/1.0.0.sql`
- Create: `plugins/database/src/test/resources/migrations-mysql/1.0.1.sql`
- Modify: `plugins/database/src/main/groovy/org/lattejava/plugin/database/DatabasePlugin.groovy`
- Test: `plugins/database/src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy`

**Interfaces:**
- Consumes: `makeConnection(databaseName, username, password)` from Task 2; `settings.migrationsDirectory`, `settings.migrationTable` from Task 1; `org.lattejava.database.migration.Migrator` (`new Migrator(Connection, Path directory, String table)`, `List<Version> migrate()` — throws `MigrationException`).
- Produces: `List<Version> migrate()` on `DatabasePlugin` (returns `org.lattejava.database.migration.Version` objects in application order, empty list when up to date) — used by Task 4's tests.

- [ ] **Step 1: Create the MySQL migration fixtures**

`src/test/resources/migrations-mysql/1.0.0.sql`:

```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
```

`src/test/resources/migrations-mysql/1.0.1.sql`:

```sql
ALTER TABLE users ADD COLUMN email VARCHAR(255);
```

- [ ] **Step 2: Write the failing test**

Add to `DatabasePluginTest.groovy`. It uses a dedicated database name (`database_migrate`) so it cannot interfere with the compare tests' `database`/`database_test`:

```groovy
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

    assertTrue(plugin.migrate().isEmpty())
  }
```

- [ ] **Step 3: Run the test to verify it fails**

Run (from `plugins/database/`, sandbox disabled): `latte test --test=DatabasePluginTest`
Expected: `mysqlMigrate` FAILS with `MissingMethodException: No signature of method ... migrate()`.

- [ ] **Step 4: Implement `migrate()`**

In `DatabasePlugin.groovy`, add imports (the explicit `Version` import deliberately overrides the `org.jooq.*` wildcard, which also contains a `Version` type):

```groovy
import org.lattejava.database.migration.MigrationException
import org.lattejava.database.migration.Migrator
import org.lattejava.database.migration.Version
```

Add the method after `ensureEqual` (keeping the class's public-methods-first layout):

```groovy
  /**
   * Runs the SQL migration scripts in {@link DatabaseSettings#migrationsDirectory} against the database named by the
   * {@link #settings} using the Latte database library. Migrations are files named {@code <semver>.sql} and are
   * applied in SemVer order over a JDBC connection that uses the execute credentials. Applied versions are recorded
   * in the {@link DatabaseSettings#migrationTable} table. Here is an example of calling this method:
   * <p>
   * <pre>
   *   database.settings.type = "postgresql"
   *   database.settings.name = "myapp"
   *   database.migrate()
   * </pre>
   *
   * @return The versions applied by this run, in application order (empty when the database is already up to date).
   */
  List<Version> migrate() {
    ensureTypeDefined()

    Path directory = project.directory.resolve(settings.migrationsDirectory)
    if (!Files.isDirectory(directory)) {
      fail("Invalid migration scripts directory [${directory}]. Set the location using:\n\n" +
          "  database.settings.migrationsDirectory = \"src/main/resources/db\"")
    }

    output.infoln("Migrating database [${settings.name}]")

    try (Connection connection = makeConnection(settings.name, settings.executeUsername, settings.executePassword)) {
      List<Version> applied = new Migrator(connection, directory, settings.migrationTable).migrate()
      if (applied.isEmpty()) {
        output.infoln("Database [${settings.name}] is already up to date")
      } else {
        applied.each { output.infoln("Applied migration [${it}]") }
      }
      return applied
    } catch (SQLException e) {
      fail("Unable to connect to database [%s] on host [%s]. Error is [%s]", settings.name, settings.host, e.message)
    } catch (MigrationException e) {
      fail("%s", e.message)
    }
    return null // Not possible but static analysis is dumb
  }
```

Notes for the implementer:
- `ChecksumException` extends `MigrationException`, so the one catch covers checksum failures too.
- `Files` and `Path` are already imported; `Connection` and `SQLException` are already imported.
- `Migrator` never closes the connection (caller owns it) — the try-with-resources here does.

- [ ] **Step 5: Run the test to verify it passes**

Run (from `plugins/database/`, sandbox disabled): `latte test --test=DatabasePluginTest`
Expected: `mysqlMigrate` PASSES (other tests unchanged).

- [ ] **Step 6: Commit**

```bash
git add src/main/groovy/org/lattejava/plugin/database/DatabasePlugin.groovy \
        src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy \
        src/test/resources/migrations-mysql
git commit -m "feat: Add migrate() to the database plugin"
```

---

### Task 4: PostgreSQL happy path (custom table) and failure-path tests

**Files:**
- Create: `plugins/database/src/test/resources/migrations-postgresql/1.0.0.sql`
- Create: `plugins/database/src/test/resources/migrations-postgresql/1.0.1.sql`
- Test: `plugins/database/src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy`

**Interfaces:**
- Consumes: `migrate()` from Task 3; `assertTypeNotDefinedMessage` helper already in the test class.
- Produces: nothing further — final verification of the feature.

- [ ] **Step 1: Create the PostgreSQL migration fixtures**

`src/test/resources/migrations-postgresql/1.0.0.sql`:

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
```

`src/test/resources/migrations-postgresql/1.0.1.sql`:

```sql
ALTER TABLE users ADD COLUMN email VARCHAR(255);
```

- [ ] **Step 2: Write the three tests**

Add to `DatabasePluginTest.groovy`. The PostgreSQL test also covers the custom `migrationTable` setting (per the spec). The failure-path tests are expected to pass immediately — they lock in the guard behavior Task 3 implemented:

```groovy
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
```

(`migrateFailsWhenDirectoryMissing` relies on the plugin project having no `src/main/resources/db` directory — the setting's default — which is true today.)

- [ ] **Step 3: Run the full suite**

Run (from `plugins/database/`, sandbox disabled): `latte test`
Expected: all tests pass, including the three new ones and all pre-existing tests.

- [ ] **Step 4: Commit**

```bash
git add src/test/groovy/org/lattejava/plugin/database/DatabasePluginTest.groovy \
        src/test/resources/migrations-postgresql
git commit -m "test: Cover PostgreSQL migrate, custom version table, and migrate failure paths"
```
