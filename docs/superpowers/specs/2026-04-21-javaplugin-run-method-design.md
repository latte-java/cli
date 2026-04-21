# JavaPlugin `run` method — design

## Purpose

Add a method to `JavaPlugin` that executes a Java main class using the project's
current module-path/classpath, the JDK version the project targets, and arbitrary
JVM and program arguments. The main class may belong to the project itself or to
any dependency (including auto-discovered modules, automatic modules, or plain
classpath JARs).

## Method signature

Added to `plugins/java/src/main/groovy/org/lattejava/plugin/java/JavaPlugin.groovy`:

```groovy
int run(Map<String, Object> attributes)
```

Groovy DSL example invocations from `project.latte`:

```groovy
// Run the project's own main class.
java.run(mainClass: "com.example.App")

// Run a dependency's tool class with extra JVM args, program args, and
// environment variables. Do not fail the build on a non-zero exit; let the
// caller inspect the returned exit code.
int rc = java.run(
    mainClass: "org.flywaydb.core.Main",
    jvmArguments: "-Xmx512m -Dfoo=bar",
    arguments: "migrate -configFiles=conf/flyway.conf",
    environment: ["FLYWAY_URL": "jdbc:postgresql://localhost/mydb"],
    failOnError: false
)
```

## Attributes

Validated via `GroovyTools.attributesValid(...)` following the same pattern as
`JavaPlugin.jarjar(...)`.

| Key | Type | Required | Default |
|---|---|---|---|
| `mainClass` | `String` | yes | — |
| `jvmArguments` | `String` | no | `""` |
| `arguments` | `String` | no | `""` |
| `dependencies` | `List<Map<String, Object>>` | no | runtime defaults (see below) |
| `additionalClasspath` | `List<Path>` | no | `[layout.mainBuildDirectory]` |
| `workingDirectory` | `Path` or `String` | no | `project.directory` |
| `environment` | `Map<String, String>` | no | `[:]` |
| `failOnError` | `boolean` | no | `true` |

**Default `dependencies`** (runtime view, symmetric with `getMainClasspath()`):

```groovy
[
    [group: "compile",  transitive: true, fetchSource: false,
        transitiveGroups: ["compile", "runtime", "provided"]],
    [group: "runtime",  transitive: true, fetchSource: false,
        transitiveGroups: ["compile", "runtime", "provided"]],
    [group: "provided", transitive: true, fetchSource: false,
        transitiveGroups: ["compile", "runtime", "provided"]],
]
```

## Behavior

1. **Lifecycle.** Call `init()` and `initialize()` (existing methods on
   `JavaPlugin`). `initialize()` is extended to also resolve a new `javaPath`
   field (`${javaHome}/bin/java`) with the same existence + executable checks
   currently applied to `javacPath` and `javaDocPath`.

2. **Path construction.** Reuse `pathString(dependencies, settings.libraryDirectories, additionalClasspath...)`.
   This honors `settings.moduleBuild` and emits either `--module-path <...>`
   or `-classpath <...>`.

3. **Entry-point resolution.** Walk the *resolved* path entries (the same
   dirs + JARs used to build the path string, including library-directory JARs
   and entries from `additionalClasspath`) and find the first entry that
   contains the `mainClass`:

    - **Directory entry**: contains `<mainClassPath>.class`. If the directory
      also contains `module-info.class`, read the module name via ASM
      (same technique as `JavaPlugin.resolveModuleName()`).
    - **JAR entry**: contains `<mainClassPath>.class`. Module name comes
      from `module-info.class` if present (explicit module) else from the
      manifest's `Automatic-Module-Name` header (automatic module) else the
      JAR is non-modular.

    Result:

    - Match in a module entry → entry point is `--module <moduleName>/<mainClass>`.
    - Match in a non-module entry while `settings.moduleBuild` is `true` → entry
      point is plain `<mainClass>`, and that specific entry is moved from the
      `--module-path` onto an additional `-classpath` (so the class loads from
      the unnamed module).
    - Match in a non-module entry while `settings.moduleBuild` is `false` → entry
      point is plain `<mainClass>`.
    - No match on any entry → `fail("Main class [%s] was not found on the resolved classpath/module-path", mainClass)`.

4. **Command assembly.**

    ```
    ${javaPath} ${jvmArguments} ${pathArgs} ${entryPoint} ${arguments}
    ```

    Tokenize via `StringTokenizer` (same approach as `JavaTestNGPlugin.test()`).

5. **Execution.**

    ```groovy
    ProcessBuilder pb = new ProcessBuilder(args)
        .inheritIO()
        .directory(workingDir.toFile())
    pb.environment().putAll(environment)   // merges into inherited env
    Process p = pb.start()
    int exitCode = p.waitFor()
    ```

6. **Exit handling.** On non-zero exit, if `failOnError` is `true`, call
   `fail("java command failed with exit code [%d]", exitCode)`. Always return
   the exit code (unreachable under `failOnError: true` because `fail(...)`
   aborts the build).

## Edge cases & failure modes

| Situation | Behavior |
|---|---|
| `mainClass` is missing | `fail(...)` via attribute validation |
| `mainClass` not found on any resolved path entry | `fail("Main class [%s] was not found ...")` |
| `javaVersion` not configured | `fail(...)` via existing `initialize()` |
| JDK for `javaVersion` not configured in properties | `fail(...)` via existing `initialize()` |
| Non-zero exit, `failOnError: true` | `fail(...)` |
| Non-zero exit, `failOnError: false` | Return non-zero exit code |
| `environment` key collides with inherited var | User-supplied value wins (merge) |
| `workingDirectory` doesn't exist | `ProcessBuilder.start()` throws `IOException` — surfaces as a build failure |

## Testing

Follow the repo convention: TestNG + EasyMock under
`plugins/java/src/test/groovy/org/lattejava/plugin/java/JavaPluginTest.groovy`.

Unit tests build a real `JavaPlugin` against a small fixture project and
assert on the command string produced. Where execution is asserted, use a
trivial main class that prints a known value and verify stdout via `inheritIO`
captured through a redirected `System.out`, or run against a separate fixture
and assert on exit code.

Cases to cover:

1. **Classpath build, project's own main class** — emits plain `<mainClass>`
   and `-classpath` includes `layout.mainBuildDirectory`.
2. **Module build, project's own main class (in project's module)** — emits
   `--module <projectModule>/<mainClass>`.
3. **Main class in a modular dependency JAR (explicit module)** — emits
   `--module <depModule>/<mainClass>`.
4. **Main class in a modular dependency JAR (automatic module via manifest)**
   — emits `--module <autoModule>/<mainClass>`.
5. **Module build but main class lives in a non-modular JAR** — emits plain
   `<mainClass>` with that JAR on `-classpath` and the remaining entries on
   `--module-path`.
6. **`mainClass` not found anywhere** — `fail(...)` with the expected message.
7. **Non-zero child exit, `failOnError: true`** — `fail(...)` is called.
8. **Non-zero child exit, `failOnError: false`** — method returns the exit code.
9. **`environment` map merges into the inherited env, not replaces it.**
10. **`workingDirectory` is honored** (the child process's `user.dir` / `pwd`
    matches the supplied path).

## Out of scope

- Running with the test classpath / separate test module — tests belong to
  the `java-testng` plugin.
- Capturing stdout/stderr into strings — `inheritIO()` only; callers who need
  capture can add a variant later.
- Asynchronous / background execution — synchronous only; `waitFor()` blocks
  until the child exits.
- Process timeouts — not included; can be added if a real use case emerges.
