# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

Latte is a Java build system (like Maven/Gradle). This repo is the CLI component — it parses Groovy-based build files (`build.latte`), resolves dependencies from Maven-compatible repositories, executes build targets, and manages plugins.

## Build System

This project is built with **Savant** (`sb` command). Java 21 is required (see `.javaversion`) to run Savant since it has not been upgraded to Java 25 yet. The project builds using Java 25 though. You can look at the setting `java.settings.javaVersion = "25"` in `build.savant` to understand the version of Java the project targets.

```bash
# Always prefix with JAVA_HOME when running build commands
JAVA_HOME=$(javaenv home) sb clean
JAVA_HOME=$(javaenv home) sb compile
JAVA_HOME=$(javaenv home) sb jar          # depends on compile
JAVA_HOME=$(javaenv home) sb test         # depends on jar (runs TestNG)
JAVA_HOME=$(javaenv home) sb int          # local integration build (depends on test)
JAVA_HOME=$(javaenv home) sb bundle       # create jlink runtime image (depends on jar)
JAVA_HOME=$(javaenv home) sb release      # full release (depends on clean, test)
```

The build file is `build.savant` (Groovy DSL, similar to Gradle). Dependencies, plugins, and targets are defined there.

## Testing

- Framework: **TestNG** with **EasyMock** for mocking
- Run all tests: `JAVA_HOME=$(javaenv home) sb test`
- Test sources: `src/test/java/`
- Test reports: `build/test-reports/`
- Integration test data: `test-project/` contains a sample `build.latte` file used by `MainTest`

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

### Key Conventions
- Artifact metadata uses `.amd` (JSON)
- The `~/.cache/latte` directory stores downloaded artifacts locally (conforms with XDG paths)
- The `~/.config/latte` directory stores the system configuration for Latte, including plugin configuration 
- Build file is `build.latte` (for projects built with Latte) or `build.savant` (for Savant, which Latte descends from)
