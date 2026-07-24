# jOOQ Database Comparison Design

**Date:** 2026-07-23
**Branch:** `feat/jooq`
**Status:** Draft

## Goal

Replace Liquibase with jOOQ as the engine behind the database plugin's `compare()` and `ensureEqual()` methods. `createDatabase()`, `createMainDatabase()`, `createTestDatabase()`, and `execute()` are untouched — they shell out to `mysql`/`psql` and never used Liquibase.

## Why

- Liquibase is a heavyweight dependency used only for schema diffing. jOOQ's `Meta` API does the same job with a smaller, more modern library (OSS edition, Apache-licensed, MySQL and PostgreSQL both supported).
- The Liquibase `DiffResult` return type leaks a third-party API into build files and forces callers to close the underlying connections themselves (see the NOTE in the current `compare()` Javadoc). The new design owns its connections and its result type.

## Public API

### `compare(left:, right:)` — breaking change

Returns a new plugin-owned class instead of Liquibase's `DiffResult`:

```groovy
class DatabaseComparison {
  /** DDL statements that would transform the left database into the right one. Empty means equal. */
  List<String> differences = []

  boolean areEqual() {
    return differences.empty
  }
}
```

Callers no longer close anything — the plugin opens and closes its JDBC connections internally. Build files that used the old `DiffResult` contract (`result.getReferenceSnapshot().getDatabase().close()` etc.) must drop those calls and use `areEqual()` / `differences` instead. The plugin version bumps to **0.5.0** to signal the break.

### `ensureEqual(left:, right:)` — signature unchanged

Still fails the build when the databases differ. The failure report becomes the list of DDL statements needed to make `left` match `right` — more actionable than Liquibase's attribute-by-attribute report. Message follows the existing style:

```
Databases [left] and [right] are not equal. To make [left] match [right] you would run:

  <DDL statement>
  <DDL statement>
```

### Unchanged behavior

- `settings.type` validation (`ensureTypeDefined()`) still guards `compare()`.
- `settings.compareUsername` / `settings.comparePassword` are still the credentials used.
- Column order is still ignored: two tables with the same columns in different physical order are equal. This is pinned by `postgresqlEnsureEqualIgnoresColumnOrder`.

### Targeted improvement

The Liquibase path hardcoded `localhost` for comparison connections while every other plugin method honors `settings.host`. The jOOQ path uses `settings.host`. Ports stay fixed (3306/5432) as today.

## Implementation

### Comparison flow

1. `ensureTypeDefined()`, validate `left`/`right` attributes (unchanged).
2. For each side: open a JDBC connection using the existing data source classes (`MysqlDataSource` / `PGSimpleDataSource`) against `settings.host`, wrapped in try-with-resources.
3. `DSL.using(connection).meta()` → filter to the schema that matters → `snapshot()` to detach from the connection (so it can close before diffing):
   - PostgreSQL: schema `public` inside the named database.
   - MySQL: the schema named after the database.
4. **Normalize schema names.** jOOQ compares objects by qualified name, so `database` vs `database_test` would naively diff as "drop one schema, create the other." Render each snapshot to DDL without schema qualification (`Settings.withRenderSchema(false)` on the exporting context), re-parse with `ctx.meta(ddlString)`, and diff the schema-less metas. This matches Liquibase's old default-schema comparison semantics and is what lets `ensureEqual(left: "database", right: "database_test")` compare content.
5. `leftMeta.migrateTo(rightMeta)` → `Queries`. Render each query to a SQL string; that list is `DatabaseComparison.differences`.

### Dependencies (`plugins/database/project.latte`)

- Remove `org.liquibase:liquibase-core:5.0.3`.
- Add `org.jooq:jooq:3.21.6`.
- Keep `com.mysql:mysql-connector-j` and `org.postgresql:postgresql` (jOOQ needs the JDBC drivers).
- Version `0.4.1` → `0.5.0`.

### Code removal

`DatabasePlugin.groovy` drops all `liquibase.*` imports, the `Scope`/`GlobalConfiguration` diff-column-order block, and `makeLiquibaseDatabase()`. Replaced by private helpers that build a connection and a normalized `Meta` snapshot per database name.

## Error handling

- Unreachable database / bad credentials: catch the jOOQ/JDBC exception and `fail()` with the database name and host in brackets, e.g. `Unable to connect to database [database_test] on host [127.0.0.1]`, plus the underlying message. Today this surfaces as a raw stack trace.
- Unsupported `settings.type` values (anything other than `mysql`/`postgresql`) are rejected by `makeConnection`, which `fail()`s with `Unsupported database type [x]`, matching the existing `createDatabase`/`execute` behavior.

## Testing

Existing tests are the spec and must pass unchanged in behavior:

- `mysqlEnsureEqual`, `postgresqlEnsureEqual` — equal databases pass. **Targeted fix folded in:** these currently compare stale leftover databases (`database_plugin`/`database_plugin_test`) instead of the ones they create (`database`/`database_test`); they will be corrected to compare the created databases, which also exercises the MySQL schema-name normalization.
- `postgresqlEnsureEqualIgnoresColumnOrder` — column order still ignored under jOOQ.
- The three `*FailsWhenTypeNotDefined` tests — validation still guards the new path.

New tests (TDD during implementation):

- `postgresqlEnsureEqualFailsWhenDifferent` — left and right differ by a column; expect `RuntimeFailureException` whose message contains the DDL for the missing column.
- `postgresqlCompareReturnsDifferences` — `compare()` on unequal databases returns `areEqual() == false` with a non-empty `differences` list; on equal databases returns `areEqual() == true`.

## Out of scope

- Comparison ports stay hardcoded (3306/5432).
- No jOOQ code generation, migrations, or query building — jOOQ is used only for `Meta` diffing.
- No changes to the shell-out methods (`createDatabase`, `execute`, …).
