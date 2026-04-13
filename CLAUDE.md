# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Latte is a Java build system (like Maven/Gradle). This repo is the CLI component — it parses Groovy-based build files (`build.latte`), resolves dependencies from Maven-compatible repositories, executes build targets, and manages plugins.

## Build System

This project is built with **Latte** (`latte` command). Java 25 is required (see `.javaversion` and `java.settings.javaVersion` in `project.latte`).

```bash
latte clean                # cleans the project
latte build                # compiles and JARs the project
latte test                 # runs tests (depends on build)
latte int                  # local integration build (depends on test)
latte bundle               # create self-contained runtime bundle (depends on build)
latte release              # full release (depends on clean, test)
```

The build file is `project.latte` (Groovy DSL). Dependencies, plugins, and targets are defined there.

### Plugins

Plugins live under `plugins/` and are also built with Latte. Each plugin has its own `project.latte`. They must be built and integration-published in dependency order since some plugins depend on others:

1. **No dependencies on other plugins:** file, dependency, database, debian, linter, pom
2. **Depends on dependency plugin:** idea, release-git
3. **Depends on dependency + file plugins:** groovy, java, groovy-testng, java-testng

To build a plugin: `cd plugins/<name> && latte int`

## Testing

- Framework: **TestNG** with **EasyMock** for mocking
- Run all tests: `latte test`
- Run a specific test: `latte test --test=ClassName`
- Test sources: `src/test/java/`
- Test reports: `build/test-reports/`
- Integration test data: `test-project/` contains a sample `project.latte` file used by `DefaultRunnerTest`

## Architecture

### Entry Point
`org.lattejava.cli.runtime.Main` — parses CLI args, loads the build file, resolves the target graph, and executes targets.

### Key Packages (under `org.lattejava`)

- **`cli.runtime`** — CLI entry point, build runner, runtime configuration
- **`cli.parser`** / **`cli.parser.groovy`** — Groovy DSL parser for `build.latte` files. `GroovyBuildFileParser` is the main parser; delegate classes handle specific DSL blocks (dependencies, workflow, publications, plugins)
- **`cli.plugin`** / **`cli.plugin.groovy`** — Plugin loading system. Plugins are loaded from dependency repos and executed as Groovy scripts
- **`cli.domain`** — Project model (`Project`, `Target`, `TargetGraph`)
- **`dep`** — Dependency resolution engine
  - **`dep.domain`** — Artifact model (`Artifact`, `ArtifactID`, `Dependencies`, `License`, `Version`)
  - **`dep.graph`** — Dependency graph construction and traversal
  - **`dep.workflow`** — Fetch/publish workflows for artifacts. Handles both Latte repos (SHA256 checksums, `.amd` metadata) and Maven repos (SHA1 checksums, `.pom` metadata)
  - **`dep.maven`** — Maven POM parsing for compatibility with Maven repositories
- **`io`** — Archive I/O: JAR (`JarBuilder`/`JarExploder`), TAR, ZIP
- **`output`** — Terminal output with ANSI 256-color support
- **`security`** — SHA1/SHA256 checksum computation and verification
- **`domain`** — Semantic versioning (`Version`, `VersionException`)
- **`util`** — Graph algorithms, path resolution (`LattePaths`)
- **`net`** — Network/download utilities

### Dependency Resolution Flow
1. Build file parsed → `Dependencies` object created with dependency groups (compile, test-compile, etc.)
2. `DependencyGraph` constructed by resolving transitive dependencies
3. Artifacts fetched via `Workflow` (tries Latte format first using `.amd`, then falls back to Maven `.pom`)
4. Checksums verified (SHA256 for Latte repos, SHA1 for Maven repos)

### License
All new source files must use the MIT license header with copyright assigned to "The Latte Project":

```
/*
 * Copyright (c) 2026, The Latte Project, All Rights Reserved
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 */
```

### Key Conventions
- Artifact metadata uses `.amd` (JSON)
- The `~/.cache/latte` directory stores downloaded artifacts locally (conforms with XDG paths)
- The `~/.config/latte` directory stores the system configuration for Latte, including plugin configuration 
- Build file is `project.latte` (Groovy DSL)
- Always create skills, rules, and commands in the project (`.claude/` directory) unless explicitly instructed to place them elsewhere
