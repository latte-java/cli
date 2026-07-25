# Database Plugin `migrate()` Method — Design

**Date:** 2026-07-25
**Status:** Approved

## Overview

Add a `migrate()` method to the database plugin (`plugins/database`) that applies SQL migration
scripts using the Latte database library (`org.lattejava:database`). The library's `Migrator`
applies `<semver>.sql` files in SemVer order over a JDBC connection, records each applied version
(with a SHA-256 checksum) in a version table, verifies checksums of previously applied migrations,
and serializes concurrent runs with advisory locks.

## Build-script API

```groovy
database.settings.type = "postgresql"          // required, as with all plugin methods
database.settings.name = "myapp"               // defaults to the project name
// optional overrides:
database.settings.migrationsDirectory = "db/migrations"   // default: src/main/resources/db
database.settings.migrationTable = "schema_versions"      // default: versions
database.migrate()
```

`migrate()` takes no arguments; it is fully settings-driven, matching `createDatabase()`.
It returns the `List<Version>` applied by the run (empty when the database is already up to
date) so build scripts can inspect or print what happened.

## Settings changes (`DatabaseSettings`)

Two new settings:

- `migrationsDirectory` (String, default `src/main/resources/db`) — location of the migration
  scripts, resolved against the project directory.
- `migrationTable` (String, default `versions`) — name of the table the `Migrator` records
  applied versions in.

Connection information is reused from the existing settings: `type`, `name`, `host`, and the
**execute** credentials (`executeUsername`/`executePassword`), since running migrations is
script execution. No new credential settings.

## `DatabasePlugin` changes

1. **Generalize `makeConnection`** — it is currently hardwired to the compare credentials.
   Change the signature to `makeConnection(String databaseName, String username, String password)`.
   The compare path (`snapshotMeta`) passes the compare credentials; `migrate()` passes the
   execute credentials. Connection URLs (ports, MySQL URL parameters) are unchanged.

2. **New `migrate()` method:**
   - `ensureTypeDefined()` (same failure message as the other methods).
   - Resolve `settings.migrationsDirectory` against `project.directory`; if it is not a
     directory, `fail()` with a message that names the `database.settings.migrationsDirectory`
     setting.
   - Open a connection to `settings.name` with the execute credentials (try-with-resources; a
     `SQLException` fails the same way `snapshotMeta` does — database name, host, error message).
   - Run `new Migrator(connection, directory, settings.migrationTable).migrate()`.
   - Catch `MigrationException` (including its `ChecksumException` subclass) and `fail()` with
     the exception message — the library's messages are already user-facing (missing directory,
     bad file names, out-of-order versions, checksum mismatches, failing statement with line
     number).
   - Output: `infoln` a "Migrating database [name]" line before the run and a summary after —
     each applied version, or "already up to date" when nothing was applied.

## Dependency changes (`plugins/database/project.latte`)

Add to the `compile` group:

```groovy
dependency(id: "org.lattejava:database:0.1.0")
```

The database library is released at `0.1.0`, so the plugin depends on the released version
directly.

## Testing

Follow the existing `DatabasePluginTest` integration-test style (live MySQL and PostgreSQL on
127.0.0.1 with the `dev`/`dev` user):

- **Fixtures:** dialect-specific migration directories, e.g.
  `src/test/resources/migrations-mysql/` and `src/test/resources/migrations-postgresql/`,
  each with two scripts (`1.0.0.sql` creates a table, `1.0.1.sql` alters it or creates a second
  table) so ordering is exercised.
- **`mysqlMigrate` / `postgresqlMigrate`:** create the test database, point
  `migrationsDirectory` at the fixture directory, call `migrate()`, assert the returned versions
  are `[1.0.0, 1.0.1]`, assert the schema and the version-table rows via the CLI clients
  (matching the existing tests' verification style). Call `migrate()` a second time and assert
  it returns an empty list (idempotent).
- **Custom table:** one happy-path test sets `migrationTable` to a non-default name and
  verifies rows land there.
- **`migrateFailsWhenTypeNotDefined`:** same pattern as the existing type-check tests.
- **`migrateFailsWhenDirectoryMissing`:** default settings, no `src/main/resources/db` in the
  test project directory → fails with a message naming the setting.

## Out of scope

- No classpath-based migrations (the library supports them; the plugin only needs directories).
- No separate migrate credentials.
- No changes to the database library itself.
