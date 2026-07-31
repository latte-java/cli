# Extract the Version class into a standalone library

**Date:** 2026-07-30
**Status:** Proposed

## Goal

Move `Version` and `VersionException` (and their tests) out of the `cli` repository into the
standalone `../version` repository, then update every `project.latte` in `cli` — including all 12
plugins — to consume that library from its integration build.

## Motivation

`Version` is a self-contained SemVer implementation with no dependencies. It is used by the `cli`,
by every plugin, and (potentially) by other Latte projects. Keeping it inside `cli` forces anyone
who wants SemVer parsing to depend on the entire build system. `../version` already exists as an
empty scaffold created for this purpose.

## Scope

### In scope

- Move 3 source files from `cli` to `../version`, repackaged.
- Update 38 import statements across 34 `cli` main and test source files.
- Update 13 reference sites across the 12 plugins.
- Bump every project in the `cli` repo plus `../version` to `0.6.0`.
- Repoint artifact dependencies at `0.6.0-{integration}`.

### Out of scope

- Releasing anything. This change leaves the ecosystem on integration builds. Cutting real `0.6.0`
  releases is a follow-up, handled by the existing `/release-plugins` flow.
- Any behavioral change to `Version`. The class moves verbatim apart from its `package` line and
  self-referential imports.
- Other repos in `~/dev/latte-java`. A search across all sibling repos found no references to
  `org.lattejava.domain` outside `cli`.

## Package rename

The `../version` scaffold already declares `module org.lattejava.version { exports org.lattejava.version; }`
and contains a `Placeholder` class in that package, so the classes are repackaged to match. This also
mirrors the `../database` precedent (`org.lattejava.database` main, `org.lattejava.database.tests` tests).

| Before                                    | After                                          |
|-------------------------------------------|------------------------------------------------|
| `org.lattejava.domain.Version`            | `org.lattejava.version.Version`                |
| `org.lattejava.domain.VersionException`   | `org.lattejava.version.VersionException`       |
| `org.lattejava.domain.VersionTest` (test) | `org.lattejava.version.tests.VersionTest`      |

The `org.lattejava.domain` package disappears from `cli` entirely — those two classes were its only
members.

`Version` refers to its own nested types by import (`Version.PreRelease.PreReleasePart.NumberPreReleasePart`,
`...StringPreReleasePart`); those imports get the same rewrite. `VersionTest` imports the same nested
types plus `Version.PreRelease`.

## Version numbering

Everything moves to `0.6.0` to keep the set aligned across what is a breaking change for every
consumer.

| Project                | Current | New   |
|------------------------|---------|-------|
| `../version`           | 0.1.0   | 0.6.0 |
| `cli`                  | 0.4.1   | 0.6.0 |
| plugin `database`      | 0.5.0   | 0.6.0 |
| plugin `debian`        | 0.4.0   | 0.6.0 |
| plugin `dependency`    | 0.4.0   | 0.6.0 |
| plugin `file`          | 0.4.3   | 0.6.0 |
| plugin `groovy`        | 0.4.0   | 0.6.0 |
| plugin `groovy-testng` | 0.4.0   | 0.6.0 |
| plugin `idea`          | 0.4.1   | 0.6.0 |
| plugin `java`          | 0.4.4   | 0.6.0 |
| plugin `java-testng`   | 0.4.0   | 0.6.0 |
| plugin `linter`        | 0.4.0   | 0.6.0 |
| plugin `pom`           | 0.4.0   | 0.6.0 |
| plugin `release-git`   | 0.4.0   | 0.6.0 |

## Two kinds of reference, handled differently

`project.latte` files contain two categories of cross-project reference, and they must be treated
differently. Conflating them produces an unbuildable repo.

### Artifact dependencies → `0.6.0-{integration}`

These are entries inside `dependencies { group(...) { ... } }`. They form an acyclic graph and can
all be repointed at integration builds, provided the projects are built in dependency order.

- `org.lattejava:cli` in every plugin's `provided` group
- `org.lattejava.plugin:dependency` and `org.lattejava.plugin:file` in the `compile` groups of
  `groovy`, `groovy-testng`, `idea`, `java`, `java-testng`, and `release-git`
- The new `org.lattejava:version` entries

### `loadPlugin(...)` ids → left unchanged

These name the plugins that *execute* the build. They are bootstrapped against **already-published**
versions, and several are self-referential:

- `plugins/dependency/project.latte` loads `org.lattejava.plugin:dependency:0.3.0`
- `plugins/groovy/project.latte` loads `org.lattejava.plugin:groovy:0.3.0`
- `plugins/groovy-testng/project.latte` loads `org.lattejava.plugin:groovy-testng:0.3.0`

Rewriting these to `0.6.0-{integration}` would require each plugin's own not-yet-produced output to
exist before it can be built — an unresolvable cycle, not merely an ordering problem. They stay at
their current published values and are picked up later by `latte upgrade plugins` during the release
flow described in `.claude/commands/release-plugins.md`.

The build tooling is unaffected by the move because the `latte` binary performing these builds is the
*installed* 0.4.x bundle, which still contains `org.lattejava.domain.Version`. Nothing being built
here has to run under the new `cli`.

## Changes to `../version`

### Files added

```
src/main/java/org/lattejava/version/Version.java
src/main/java/org/lattejava/version/VersionException.java
src/test/java/org/lattejava/version/tests/VersionTest.java
.javaversion                                              (contents: 25, matching cli)
```

### Files deleted

```
src/main/java/org/lattejava/version/Placeholder.java
src/test/java/org/lattejava/version/tests/PlaceholderTest.java
```

### Files modified

- `project.latte` — version to `0.6.0`; TestNG `7.10.2` → `7.12.0` to match the version `cli` tested
  `VersionTest` against.
- `src/main/java/module-info.java` and `src/test/java/module-info.java` — add the SPDX copyright
  header. The repo's own `.claude/rules/copyright.md` requires it on every Java file *including*
  `module-info.java`, and both currently lack it. Module declarations themselves are already correct
  and need no change.

The moved files already carry the correct `Copyright (c) 2022-2026 The Latte Project` / SPDX header,
so headers are preserved as-is.

## Changes to `cli`

### Files deleted

```
src/main/java/org/lattejava/domain/Version.java
src/main/java/org/lattejava/domain/VersionException.java
src/test/java/org/lattejava/domain/VersionTest.java
```

### `project.latte`

Version to `0.6.0`, and one dependency added to the `compile` group (alphabetical placement puts it
after the `org.apache.*` entries):

```groovy
group(name: "compile") {
  dependency(id: "com.googlecode.json-simple:json-simple:1.1.1")
  dependency(id: "org.apache.commons:commons-compress:1.28.0")
  dependency(id: "org.apache.groovy:groovy:5.0.5")
  dependency(id: "org.lattejava:version:0.6.0-{integration}")
}
```

`loadPlugin` ids in this file are left at 0.4.x per the rule above.

### Import rewrites

38 import statements across 24 main-source files and 10 test files. Three forms appear:

| Form                                            | Count | Becomes                                          |
|-------------------------------------------------|-------|--------------------------------------------------|
| `import org.lattejava.domain.Version;`          | 25    | `import org.lattejava.version.Version;`          |
| `import org.lattejava.domain.VersionException;` | 10    | `import org.lattejava.version.VersionException;` |
| `import org.lattejava.domain.*;`                | 3     | `import org.lattejava.version.*;`                |

The wildcard form appears in `WorkflowDelegate.java`, `Main.java`, and `GroovyProjectFileParserTest.java`.

Because `.claude/rules/code-conventions.md` requires alphabetized imports and `domain` sorts before
`version`, each rewritten import is repositioned within its `org.lattejava.*` import group where the
file's existing block is alphabetized. Files whose blocks are not already sorted are left in their
existing relative order rather than opportunistically reformatted.

## Changes to the 12 plugins

Every plugin needs the same three edits. All 12 reference `Version` — 11 in tests only, plus
`dependency`, which uses it in both its tests and its main source (`DependencyPlugin.groovy`).

1. **`project.latte` version** → `0.6.0`.
2. **`project.latte` `provided` group** — repoint `cli`, add `version`:

   ```groovy
   group(name: "provided") {
     dependency(id: "org.lattejava:cli:0.6.0-{integration}")
     dependency(id: "org.lattejava:version:0.6.0-{integration}")
   }
   ```

3. **Groovy imports** — `import org.lattejava.domain.Version` → `import org.lattejava.version.Version`.
   `plugins/debian` uses the fully-qualified form inline instead
   (`new org.lattejava.domain.Version("1.0")` in `DebianPluginTest.groovy:56`) and gets the same
   rewrite.

Additionally, the six plugins with inter-plugin `compile` dependencies repoint those at
`0.6.0-{integration}`: `groovy`, `groovy-testng`, `java`, `java-testng` (each on `dependency` and
`file`), and `idea`, `release-git` (each on `dependency` only).

`plugins/database` keeps `org.lattejava:database:0.1.0` unchanged — a separate library outside this
change.

## Build order

The repointed artifact dependencies impose a strict order. Each step is `latte int` from that
directory.

```
1. ../version
2. cli
3. plugins/dependency
4. plugins/file
5. plugins/groovy          plugins/groovy-testng    plugins/java
   plugins/java-testng     plugins/idea             plugins/release-git
6. plugins/database        plugins/debian           plugins/linter    plugins/pom
```

Steps 5 and 6 have no ordering constraints within themselves. Step 5 needs `dependency` and/or
`file`; step 6 needs only `cli`.

## Verification

- `latte test` in `../version` — `VersionTest` passes in its new module. This is the real check that
  the move is faithful; the test file is unchanged apart from its package and imports.
- `latte test` in `cli` — full suite passes with `Version` arriving from the external jar.
- `latte test` in each of the 12 plugins.
- `grep -rn "lattejava.domain" --include="*.java" --include="*.groovy" --include="*.latte"` over the
  `cli` repo returns nothing outside `build/` output directories.

`cli`'s plugin-loading tests are unaffected: `DefaultPluginLoaderTest` loads synthetic fixture jars
from `src/test/plugin-repository`, not real published plugins, so no old plugin compiled against
`org.lattejava.domain` is ever loaded during the test run.

## Documentation

`CLAUDE.md` in `cli` documents the plugin build order. It gains a note that `../version` must be
integration-built before `cli`, and `cli` before the plugins.

## Follow-up work (not part of this change)

Releasing `0.6.0` for real requires, in order: releasing `../version` 0.6.0; running
`latte upgrade dependencies` in `cli` so its `compile` group points at the released `0.6.0` rather
than `0.6.0-{integration}`; releasing `cli`; then `/release-plugins`, whose
`latte upgrade dependencies` / `latte upgrade plugins` steps resolve every remaining integration
reference and the stale `loadPlugin` ids.

Publishing any project while it still depends on an `-{integration}` artifact would emit metadata
pointing at a version that exists only on one developer's machine, so the upgrade step is mandatory
before release, not optional.
