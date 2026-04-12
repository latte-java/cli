# Upgrade Command Design

## Overview

Add an `upgrade` global command to the Latte CLI that can upgrade the Latte runtime, plugins, and dependencies.

## Invocation

```
latte upgrade <parameter>
```

### Parameters

| Parameter      | Description                                                                   |
|----------------|-------------------------------------------------------------------------------|
| `all`          | Upgrades the Latte runtime, dependencies, and all plugins in the project file |
| `runtime`      | Upgrades only the Latte runtime                                               |
| `plugins`      | Upgrades all plugins in the project file to their latest versions             |
| `dependency`   | Upgrades a single dependency (same mechanism as `install`)                    |
| `dependencies` | Upgrades all project dependencies to their latest versions                    |
| `help`         | Displays usage information for the upgrade command                            |

## Runtime Upgrade (`runtime` / `all`)

1. Read current version from `Main.class.getPackage().getImplementationVersion()`
2. Query GitHub Releases API (`GET https://api.github.com/repos/latte-java/cli/releases/latest`) for latest version and tarball URL
3. If already on latest, print info and skip
4. Download `latte-{version}.tar.gz` to a temp file
5. Extract to a temp directory
6. Replace contents of `latte.home` (`bin/`, `lib/`, `templates/`) with extracted files
7. Print success with old and new versions

Install location from `System.getProperty("latte.home")`.

## Plugin Upgrade (`plugins` / `all`)

For each `loadPlugin(id: "...")` in the project file:

1. Extract the plugin artifact ID (e.g., `org.lattejava.plugin:dependency`)
2. Query `https://api.lattejava.org/repository/search?id=<id>&latest=true` for the latest version
3. If already on latest, skip
4. Update the version in the `loadPlugin` line in the project file

Reuse the project file parsing/rewriting logic from `InstallCommand`.

## Dependency Upgrade (`dependency`)

```
latte upgrade dependency <group:name:version>
```

Uses the same mechanism as the `install` command to resolve and update a single dependency in the project file. If the Groovy lexer is able to retain comments while regnerating the `dependencies` block, the code should be updated to preserve comments.

## Dependencies Upgrade (`dependencies`)

For each dependency in the project file:

1. Extract the artifact ID
2. Query `https://api.lattejava.org/repository/search?id=<id>&latest=true` for the latest version
3. If already on latest, skip
4. Update the version in the project file

## HTTP

Use `java.net.http.HttpClient` for all HTTP requests (GitHub API, Latte repository API, tarball download). Parse JSON responses with json-simple.

## Update Safety (Runtime)

Extract tarball to a temp directory. Only after successful extraction, delete existing `bin/`, `lib/`, `templates/` under `latte.home` and move the new ones in.

## Error Handling

- `latte.home` not set (for `runtime`/`all`): fail with message
- No project file (for `plugins`/`dependencies`/`all`): fail with message
- Network errors: fail with descriptive message
- Artifact not found in repository: print warning, skip that artifact
- Already on latest: print info, skip

## Integration

- New class: `org.lattejava.cli.command.UpgradeCommand` implementing `Command`
- Register in `DefaultRunner.COMMANDS` as `"upgrade"`
- Add to help text in `Main.printHelp()`
- Reuse code from `InstallCommand` for project file modification

## Dependencies

Requires the repository search Cloudflare Worker (see `2026-04-12-repository-search-worker-design.md`) to be deployed for plugin and dependency upgrades. The runtime upgrade only depends on the GitHub Releases API.
