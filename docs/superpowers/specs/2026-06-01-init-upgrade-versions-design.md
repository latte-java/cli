# Design: Upgrade dependency and plugin versions after `latte init`

**Date:** 2026-06-01
**Branch:** `features/upgrade-templates`
**Status:** Approved

## Problem

`latte init` scaffolds a new project from a template (`library`, `web`, or a custom
directory). The templates pin specific plugin and dependency versions in `project.latte`
(e.g. `org.lattejava.plugin:java:0.2.0`, `org.lattejava:http:0.1.5`). Those pins drift
out of date as new versions are published, so a freshly created project starts life
behind the latest releases. We want `init` to bump the plugin and dependency versions in
the generated `project.latte` to the latest available immediately after scaffolding, by
reusing the existing `UpgradeCommand`.

## Decisions

| Question                          | Decision                                                                                         |
|-----------------------------------|--------------------------------------------------------------------------------------------------|
| When does the upgrade run?        | On by default; suppressed with a `--no-upgrade` switch.                                           |
| What is upgraded?                 | Both plugins (`loadPlugin`) and dependencies (`dependency`).                                      |
| How is it done?                   | Init delegates to `UpgradeCommand`, which is reworked to edit `project.latte` purely with the lexer. |
| Artifacts not in the Latte repo   | Existing `UpgradeCommand` behavior: log a "not found, skipping" line and leave the version as-is. |
| Quiet output for init             | New `--no-warnings` switch on `UpgradeCommand` suppresses the "not found, skipping" lines.        |
| Offline / API down                | No special handling. Lookups return `null` (already handled), so nothing upgrades and init still succeeds. |

Maven-only artifacts (e.g. `org.testng:testng`) are not in the Latte repository, so the
search API returns nothing for them and they keep their template version. This is the
same already-implemented "not found, skipping" path and is not a failure.

## Why the lexer-only rework

The existing `UpgradeCommand` is split across two mechanisms:
- `upgradePlugins()` edits the **source text** of `project.latte` with the Groovy lexer
  (`GroovySourceTools.findMethodCallStringArguments`), rewriting the version inside each
  `loadPlugin(id: "...")`. It only needs `project.directory`.
- `upgradeDependencies()` / `upgradeDependency()` operate on a **parsed `Project`** and
  regenerate the whole `dependencies` block via `ProjectFileTools.writeDependencies`.

Reworking the dependency methods to use the lexer like the plugin method gives three
wins:
1. **Comments and whitespace are preserved.** Block regeneration discards them; a lexer
   pass touches only the version substring.
2. **No parser, no plugin loading.** Parsing a `project.latte` executes `loadPlugin(...)`
   (`ProjectBuildFile.java:101-103` → `DefaultPluginLoader.load()`) and resolves the full
   graph. The lexer needs none of that, so init can upgrade the new file cheaply with an
   unparsed `Project`.
3. **One mechanism.** `loadPlugin(...)` and `dependency(id: ...)` share the same
   `name(id: "...")` shape, so both are the same lexer pass with a different method name.

## Approach

### Changed: `UpgradeCommand` (`org.lattejava.cli.command`)

- **`upgradeDependencies()` — reworked to the lexer.** Read `project.latte`, find every
  `dependency(id: "...")` string literal via
  `GroovySourceTools.findMethodCallStringArguments(content, "dependency")`, split each id
  on the last `:` into `artifactId` and `currentVersion`, call
  `RepositoryTools.queryLatestVersion(artifactId)`, and rewrite the version substring in
  place (iterating in reverse so offsets stay valid — the same pattern as
  `upgradePlugins`). Write the file once if anything changed. No parsed `Project`, no
  `writeDependencies`.
- **`upgradeDependency()` (single) — reworked to the lexer.** Validate the requested
  artifact id with `ArtifactID`, find the `dependency(id: "group:project:...")` whose id
  matches that `group:project`, and rewrite its version (to the explicit arg, or the
  looked-up latest). Preserve the existing "not found" error when no match exists.
- **`upgradePlugins()` — unchanged** (already lexer-based).
- **`upgradeRuntime()` — unchanged** (downloads/replaces the runtime; not file editing).
- **New `--no-warnings` switch.** `run()` reads `configuration.switches.has("no-warnings")`
  and threads the boolean into the upgrade helpers (their signatures gain a
  `noWarnings` parameter, e.g. `upgradePlugins(output, project, noWarnings)`). When true,
  suppress the per-artifact "not found in Latte repository, skipping" lines. The
  "Upgrading X from a to b" and "already at latest" lines still print. Applies to
  `latte upgrade` generally; init passes it.
- The exclusion closure (`dependency(id: "...") { exclusion(id: "...") }`),
  `skipCompatibilityCheck: true`, and non-semantic versions are preserved automatically:
  the lexer only matches the id string inside the `dependency(...)` parens and never
  touches the trailing closure or other attributes.

`ProjectFileTools.writeDependencies` / `replaceDependenciesBlock` /
`generateDependenciesBlock` are **retained** — `InstallCommand` (`InstallCommand.java:101`)
still uses them. `UpgradeCommand` simply stops calling them.

### Changed: `InitCommand` (`org.lattejava.cli.command`)

After `copyTemplate(...)` succeeds, unless `configuration.switches.has("no-upgrade")` and
only if `project.latte` exists in the project directory:
1. Build an unparsed `new Project(projectDir, output)` (sufficient — the reworked upgrade
   methods need only `project.directory`).
2. Retrieve `UpgradeCommand` from `DefaultRunner.COMMANDS`.
3. Invoke it for `plugins` then `dependencies` with the `--no-warnings` switch set. Order
   is plugins first, then dependencies, so the dependency pass reads the
   plugin-updated file.

Notes:
- The `project.latte`-exists guard means custom templates without a `project.latte`
  silently skip the upgrade rather than failing.
- `--no-upgrade` and `--no-warnings` parse for free as boolean switches via
  `DefaultRuntimeConfigurationParser`. The template name stays the first positional
  `args` entry.
- The "Created project ..." message remains.

## Data flow

```
latte init [template] [--no-upgrade]
  -> resolve template dir
  -> prompt group/name/license
  -> copyTemplate (writes project.latte + sources)
  -> if !--no-upgrade and project.latte exists:
       project   = new Project(projectDir, output)         // unparsed
       upgrade   = DefaultRunner.COMMANDS.get("upgrade")
       upgrade.run(config[args=[plugins],     switches=[no-warnings]], output, project)
       upgrade.run(config[args=[dependencies], switches=[no-warnings]], output, project)
  -> "Created project [group:name] from template [..]"

UpgradeCommand.upgradeDependencies / upgradeDependency / upgradePlugins:
  content = read(project.latte)
  literals = findMethodCallStringArguments(content, "dependency" | "loadPlugin")
  for each (reverse): split id:version; latest = queryLatestVersion(id)
     latest != null && != current -> rewrite version substring
     latest == null               -> if !no-warnings: log "not found, skipping"
     latest == current            -> log "already at latest"
  if changed: write(project.latte, content)
```

## Error handling

- `RepositoryTools.queryLatestVersion` already returns `null` on not-found, non-200, and
  any exception (offline/timeout). All `null` results are handled the same way: skip,
  optionally log. Nothing throws, so init always succeeds with at worst the template
  versions left in place.
- Pre-existing hard errors are unchanged (template not found, target file already exists,
  unreadable/unwritable files, invalid artifact id for the single-dependency command).

## Testing

**`UpgradeCommandTest` (rework existing):**
- The existing dependency tests (`upgradeDependencyWithVersion`,
  `upgradeDependencyInTestGroup`, `upgradeDependencyPreservesOtherDependencies`,
  `upgradeDependencyPreservesExclusions`, `upgradeDependencyPreservesSkipCompatibilityCheck`,
  `upgradeDependencyPreservesNonSemanticVersion`, `upgradeDependenciesUpgradesAll`,
  `upgradeDependenciesSkipsUnknown`, etc.) are re-verified against the lexer
  implementation. Outcome assertions (file reparses with the right versions; other deps,
  exclusions, and attributes preserved) should hold; messages that referenced a
  dependency's group name are reworded since the lexer pass doesn't know groups.
- `upgradeDependenciesUpgradesAll` / `upgradePluginsUpgradesAll` remain **live-network**
  integration tests (they already hit `api.lattejava.org`), consistent with the current
  suite.
- Add a test that `--no-warnings` suppresses the "not found, skipping" line for an
  unknown artifact while still upgrading a known one.

**`InitCommandTest` (additions):**
- `--no-upgrade` skips the upgrade entirely (no network) and still creates the project
  with the template versions intact.
- The two scaffolding end-to-end tests (`initLibraryTemplateEndToEnd`,
  `initWebTemplateEndToEnd`) pass `--no-upgrade` so they stay hermetic and keep asserting
  only scaffolding output.
- Existing init tests using the in-memory `TEST_PROJECT_LATTE` (no `loadPlugin` /
  `dependency` calls) are unaffected: the upgrade finds no matches and performs no
  lookups.

## Out of scope

- Upgrading Maven-repository dependencies (the search API does not resolve them).
- Changing `upgradeRuntime()` behavior.
- Changing `InstallCommand` or removing `ProjectFileTools.writeDependencies`.
- Interactive prompting to confirm individual upgrades.
- Any change to `RepositoryTools`.
