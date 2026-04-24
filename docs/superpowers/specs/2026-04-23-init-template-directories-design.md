# `latte init` template directories

## Background

`latte init` today copies a single template file (`project.latte`) from
`$latte.home/templates/project.latte` into the current directory, substituting
`${group}`, `${name}`, and `${license}` after prompting the user. An optional
`--template=<file>` switch points at a different single file. After writing the
project file, the command hardcodes creation of the standard Java source
layout (`src/main/java`, `src/main/resources`, `src/test/java`,
`src/test/resources`).

We want the init command to scaffold more than one file, so a Latte project
type can include whatever combination of files and directories it needs. We
also want a second built-in template — `web` — that bootstraps a project
using `org.lattejava:web`.

## Goals

- Turn the template concept from "one file" into "one directory tree per
  project type."
- Ship two built-in templates: `library` (the current behavior, repackaged)
  and `web`.
- Let users point `latte init` at an arbitrary directory on disk to use their
  own template.
- Keep the existing interactive prompts unchanged; extend variable
  substitution with a small set of derived variables (`${nameId}`,
  `${package}`, `${packagePath}`) and apply substitution to both file
  contents and path segments.

## Non-goals

- No support for binary files in templates; everything is read and written as
  UTF-8. If this ever matters, we can add an extension-based skip later.
- No `--force` flag for overwriting existing files. Conflict is an error.
- No tightening of the project-name validator. Hyphens stay legal in names;
  they're translated to underscores only when producing a Java identifier
  (module / package name).

## CLI surface

```
latte init                 # uses built-in "library"
latte init web             # uses built-in "web"
latte init ./my-template   # uses custom directory on disk
latte init ~/templates/foo # tilde-expanded to $HOME
```

The `--template` switch is removed.

### Name-vs-path detection

The argument is treated as a path if any of:

- It contains `/`.
- It contains `\`.
- It starts with `~` (expanded to `$HOME` before resolving).
- `Path.of(arg).isAbsolute()`.

Otherwise the argument is a template name. This is purely syntactic — no
filesystem probing to decide. Predictable and stable regardless of what
happens to exist in the current directory. If a user really has a local
template directory named `library`, they invoke it as `./library`.

### Resolution rules

- **Path-like:** expand `~`, then `Path.of(expanded)`. Must be an existing
  directory or the command fails with
  `Template directory not found: [<path>]`.
- **Name-like:** require the `latte.home` system property (same guarantee as
  today). Resolve to `$latte.home/templates/<name>`. Must be an existing
  directory or the command fails with
  `Template [<name>] not found at [<path>]. Is Latte installed correctly?`.

## Template variables

The following variables are available for `${...}` substitution. The first
three are user-provided; the rest are derived. Derivation happens once, after
prompts, before any copying.

| Variable          | Value                                                         | Example input        | Example output        |
|-------------------|---------------------------------------------------------------|----------------------|-----------------------|
| `${group}`        | Raw group as entered                                          | `org.example`        | `org.example`         |
| `${name}`         | Raw project name as entered                                   | `my-lib`             | `my-lib`              |
| `${license}`      | SPDX identifier                                               | `Apache-2.0`         | `Apache-2.0`          |
| `${nameId}`       | `${name}` with `-` → `_` (valid Java identifier)              | `my-lib`             | `my_lib`              |
| `${package}`      | `${group}.${nameId}` — the Java package / module name         | —                    | `org.example.my_lib`  |
| `${packagePath}`  | `${package}` with `.` → `/` — filesystem path for the package | —                    | `org/example/my_lib`  |

The same substitution function applies to **both file contents and relative
file paths** inside templates. That makes `${packagePath}` usable as a
directory name in the template tree, e.g.
`src/main/java/${packagePath}/Placeholder.java`. Path segments are
substituted after walking the template and before checking for conflicts /
writing.

## Copy semantics

`InitCommand.run` stages its work in three phases so it never leaves a
half-initialized project behind:

1. **Prompt.** Ask for group, name, license (unchanged behavior, unchanged
   prompts and validation). Then derive `${nameId}`, `${package}`,
   `${packagePath}` from the answers.
2. **Preflight.** Walk the resolved template directory and collect every
   file's template-relative path. For each, compute the **resolved** target
   path by substituting variables in each path segment, then verify the
   target path does not already exist as a regular file. If any does, fail
   with `[<resolved relative path>] already exists` and write nothing. This
   subsumes today's `project.latte` early-exit check — when the template
   includes `project.latte`, a preexisting `project.latte` is the first
   thing the preflight rejects.
3. **Write.** For each collected file, read the source as UTF-8, substitute
   variables in the file's contents, create the target's parent directories,
   and write the target as UTF-8.

Prompts happen before the preflight because the preflight needs the derived
variables to compute resolved target paths. That's a behavioral change from
today (which checks `project.latte` before prompting), but the common case
— a brand-new directory — is unaffected, and no files are written before
the preflight passes. Users with a conflict still get a clean abort; they
just answer the prompts first.

Directories are created lazily when their first file is written. Empty dirs in
templates must carry a sentinel file (`.gitkeep`) because `FileSet.toFileInfos`
walks files only, not empty directories — so empty dirs would never survive
the bundle copy. `.gitkeep` files are copied verbatim (no stripping) and land
in the user's project as zero-byte files.

The existing `createDirectoryLayout` method is deleted. Each template now
owns the directory structure it needs.

## `library` template

Contents of `src/main/templates/library/`:

```
project.latte                                           # same as today
src/main/java/module-info.java
src/main/java/${packagePath}/Placeholder.java
src/main/resources/.gitkeep
src/test/java/${packagePath}/PlaceholderTest.java
src/test/resources/.gitkeep
```

No `.gitkeep` under `src/main/java` or `src/test/java` — the package
directories created by `Placeholder.java` and `PlaceholderTest.java` already
make those parents non-empty.

### `project.latte`

Byte-identical to today's template.

### `module-info.java`

```java
module ${package} {
}
```

Empty module declaration. Users add `requires` / `exports` as they build out
the library.

### `Placeholder.java`

```java
package ${package};

public class Placeholder {
}
```

Minimal placeholder so the project compiles immediately and so the package
directory exists for users to drop real source files into. The class name
`Placeholder` makes it obvious this is meant to be deleted or renamed.

### `PlaceholderTest.java`

```java
package ${package};

import org.testng.annotations.Test;

public class PlaceholderTest {
  @Test
  public void placeholder() {
  }
}
```

Gives `latte test` something real to run immediately. Uses a regular class
import rather than `import module org.testng` because TestNG is a library,
not a named module.

## `web` template

Contents of `src/main/templates/web/`:

```
project.latte
src/main/java/Main.java
src/test/java/MainTest.java
web/static/.gitkeep
```

### `project.latte`

```groovy
project(group: "${group}", name: "${name}", version: "0.1.0", licenses: ["${license}"]) {
  workflow {
    standard()
  }

  dependencies {
    group(name: "compile") {
      dependency(id: "org.lattejava:web:0.1.0")
    }
    group(name: "test-compile", export: false) {
      dependency(id: "org.testng:testng:7.10.2")
    }
  }

  publications {
    standard()
  }
}

// Plugins
dependency = loadPlugin(id: "org.lattejava.plugin:dependency:0.1.0")
java = loadPlugin(id: "org.lattejava.plugin:java:0.1.0")
javaTestNG = loadPlugin(id: "org.lattejava.plugin:java-testng:0.1.0")
release = loadPlugin(id: "org.lattejava.plugin:release-git:0.1.0")

// Plugin settings
java.settings.javaVersion = "25"
javaTestNG.settings.javaVersion = "25"

target(name: "clean", description: "Cleans the project") {
  java.clean()
}

target(name: "build", description: "Compiles and JARs the project") {
  java.compile()
  java.jar()
}

target(name: "test", description: "Runs the project's tests", dependsOn: ["build"]) {
  javaTestNG.test()
}

target(name: "run", description: "Runs the web server") {
  java.run(main: "src/main/java/Main.java")
}

target(name: "int", description: "Releases a local integration build of the project", dependsOn: ["test"]) {
  dependency.integrate()
}

target(name: "release", description: "Releases a full version of the project", dependsOn: ["clean", "test"]) {
  release.release()
}
```

The `run` target intentionally passes a source file path to `java.run`. The
Java plugin's `run` method supports source-file execution (JEP 330/458). The
generated project is therefore not a named module — `import module` works
with the dependency path supplied by the java plugin.

If `import module org.lattejava.web` turns out not to resolve under
source-file execution in practice, we revisit: either add a `module-info.java`
and a templated package, or fall back to `java.compile()` + `java.run` on a
compiled main class. That investigation is out of scope for this spec.

### `src/main/java/Main.java`

```java
import module org.lattejava.web;

void main() {
  new Web()
      .get("/", (req, res) -> res.setBody("Hello, world!"))
      .start(8080);
}
```

No class, no package. Uses Java 25's instance main methods and unnamed
classes (JEP 512 / 445) plus module imports (JEP 511 / 503). Kept as minimal
as possible — the framework's shutdown hook handles `close()`.

### `src/test/java/MainTest.java`

```java
import module org.testng;

public class MainTest {
  @Test
  public void placeholder() {
  }
}
```

A placeholder test so `latte test` has something real to run out of the box.
Users replace it with their own tests.

### `web/static/.gitkeep`

Empty file. Exists so `web/static/` ships in the bundle and lands in the
user's project directory, ready for static assets.

## Bundle target

`project.latte` and `build.savant` already copy the templates directory:

```groovy
file.mkdir(dir: "${bundleDir}/templates")
file.copy(to: "${bundleDir}/templates") {
  fileSet(dir: "src/main/templates", includePatterns: [/.*/])
}
```

`FileSet.toFileInfos` uses `Files.walkFileTree`, which recurses, so nested
template subdirectories are copied correctly with no change to the build
files. Empty directories are not copied (walkFileTree's `visitFile` fires
only for files), which is why `.gitkeep` files are required in empty
template directories.

## `InitCommand` changes

- Replace `loadTemplate(RuntimeConfiguration)` with
  `resolveTemplateDir(RuntimeConfiguration, List<String> positionalArgs)`,
  applying the resolution rules above.
- Add a `substitute(String, Map<String, String>)` helper that replaces
  `${key}` with `map.get(key)`. The same helper is applied to both path
  segments and file contents.
- Add a `deriveVariables(String group, String name, String license)`
  helper that returns the full variable map including `${nameId}`,
  `${package}`, and `${packagePath}`.
- Replace inline string substitution + `Files.writeString` with a
  `copyTemplate(Path templateDir, Path projectDir, Map<String, String> vars)`
  method that walks the template, resolves each relative target path through
  `substitute`, preflights conflicts, then writes.
- Delete `createDirectoryLayout`.
- Preserve all prompt methods and validation logic unchanged.

The `run(RuntimeConfiguration, Output, Project)` entry point needs access to
the init command's positional argument (the template name or path). Wherever
`InitCommand` is dispatched from today, it must now receive the first
positional argument of `latte init` (or null if none was given). The current
dispatcher already hands the `RuntimeConfiguration` over; the positional
arguments hang off the same `switches`/`arguments` structure. Exact wiring
is an implementation detail for the plan.

## Error messages

Following `.claude/rules/error-messages.md`, all runtime values go in square
brackets:

- `[<path>] already exists` — preflight conflict.
- `Template directory not found: [<path>]` — path-like arg doesn't resolve
  to a directory.
- `Template [<name>] not found at [<path>]. Is Latte installed correctly?` —
  named template missing from `$latte.home/templates/`.
- `The latte.home system property is not set. Is Latte installed correctly?`
  — unchanged.
- `Failed to read template file [<path>]: <reason>` / `Failed to write
  [<path>]: <reason>` — IO failures.

## Tests

`InitCommandTest` is rewritten around temporary template **directories**.

Existing cases to keep (adapted for directories):

- `init` — custom template directory produces expected `project.latte`.
- `initWithMIT` — different license substitution.
- `initWithExistingDirectories` — no-op when dirs already exist.
- `initWithInvalidInputThenValid` — prompt loops still work.
- `initAlreadyExists` — existing `project.latte` aborts via the preflight,
  message still mentions `project.latte`.
- `initWithDefaults` — name defaults to dir, license defaults to MIT.
- `initOverrideDefaults` — explicit values beat defaults.
- `initWithCustomTemplate` — now points at a directory, asserts multiple
  files land.
- `initWithMissingTemplate` — now a missing directory, not a missing file.

New cases:

- `initDefaultsToLibrary` — no argument, named-template lookup finds
  `$latte.home/templates/library`. Stub `latte.home` for the test.
- `initNamedTemplate` — `latte init web` resolves to
  `$latte.home/templates/web`.
- `initPathTemplate` — explicit `./path` or absolute path.
- `initTildeTemplate` — `~/foo` expands to `$HOME/foo`.
- `initPreflightAbortsOnConflict` — existing non-`project.latte` target
  file triggers the preflight error and **no** files are written.
- `initSubstitutesAllFiles` — variables replaced in files beyond
  `project.latte`.
- `initGitkeepPreserved` — `.gitkeep` files survive with empty contents.
- `initSubstitutesPathSegments` — a template file at
  `a/${packagePath}/b.txt` lands at `a/org/example/my_lib/b.txt` when the
  group is `org.example` and the name is `my-lib`.
- `initHyphenatedNameProducesUnderscoreIdentifier` — name `my-lib` →
  `${nameId}` = `my_lib`, `${package}` = `<group>.my_lib`, and the generated
  `Placeholder.java` has `package <group>.my_lib;`.
- `initLibraryTemplateSmoke` — run against a fixture mirroring the real
  library template and verify `module-info.java`, `Placeholder.java`, and
  `PlaceholderTest.java` land at the correct resolved paths with the correct
  substituted contents.

## Risks

- **Source-file run + `import module`** may not actually work as hoped. The
  spec says "if not, revisit" — the plan should include a manual verification
  step where someone scaffolds a web project and runs `latte run` before we
  call the feature done.
- **Backward compatibility of `--template`.** Removing the switch is a
  breaking change. Acceptable pre-1.0; the plan should make the removal
  explicit and update README/docs.
