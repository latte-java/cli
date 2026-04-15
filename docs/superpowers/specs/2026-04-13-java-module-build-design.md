# Java Plugin Module Build Support

## Summary

Add optional JPMS module build support to the Java plugin. When enabled, compilation and javadoc use `--module-path` instead of `-classpath`, test compilation uses `--patch-module` to inject test classes into the main module, and the module name is extracted from compiled `module-info.class` using ASM.

## Settings

Add a `moduleBuild` field to `JavaSettings`:

```groovy
boolean moduleBuild = false
```

The default is `false`. In the `JavaPlugin` constructor, after the settings object is created, check if `module-info.java` exists in `mainSourceDirectory` and flip `settings.moduleBuild = true` if found. The user can then override this in `project.latte` after plugin load:

```groovy
java.settings.moduleBuild = false  // force off even if module-info.java exists
java.settings.moduleBuild = true   // force on explicitly
```

## Module Name Extraction

After `compileMain()` produces `module-info.class` in `mainBuildDirectory`, parse it with ASM's `ClassReader` and `ClassVisitor.visitModule()` to extract the module name. Store the result as an instance field (`String moduleName`) on `JavaPlugin`.

Uses `org.ow2.asm:asm:9.9.1` (same version as the dependency plugin).

## Compilation Changes

### `compileMain()` in module mode

- Replace `-classpath <deps>` with `--module-path <deps>`
- Keep `-sourcepath` and `-d` as-is
- After successful compilation, parse `module-info.class` to extract and store the module name

### `compileTest()` in module mode

- Use `--module-path <deps>:<mainBuildDirectory>` so the main module and its dependencies are available as modules
- Add `--patch-module <moduleName>=<testSourceDirectory>` to inject test source files into the main module
- Add `--add-reads <moduleName>=ALL-UNNAMED` so the module can access test framework classes on the classpath
- Test framework JARs (TestNG, EasyMock, etc.) that aren't proper modules remain on `-classpath`

### `document()` in module mode

- Replace `-classpath <deps>` with `--module-path <deps>`
- Add `--module <moduleName>` to generate docs for the module

## Classpath/Module-path Helper

Refactor the private `classpath()` method to accept a mode parameter or split into two methods so it can produce either `-classpath <paths>` or `--module-path <paths>` strings depending on the build mode.

## Auto-detection Logic

In the `JavaPlugin` constructor, check if `module-info.java` exists in `layout.mainSourceDirectory` (resolved against `project.directory`). If it does, set `settings.moduleBuild = true`. The user can override this afterward in `project.latte`.

## Dependencies

Add `org.ow2.asm:asm:9.9.1` to the java plugin's `project.latte` compile dependencies.

## What Doesn't Change

- `jar()` / `jarInternal()` — modular JARs are just JARs with `module-info.class`, already handled
- `JavaLayout` — no changes
- `clean()` — no changes
- `Classpath` class — no changes (we just call `toString("--module-path ")` instead of `toString("-classpath ")`)
