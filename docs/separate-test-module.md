# Separate Test Module Mode (JPMS)

The `java` and `java-testng` plugins support running tests as an independent JPMS module that `requires` the main module, instead of being patched into it. This enables **public-API-only testing** — tests physically cannot access internals, so accidentally relying on non-exported packages fails at compile time.

## When to use

Use separate-test-module mode when you want tests to be limited to the main module's exported API. Use the default patch-module mode when you need to test internals (package-private classes, `exports ... to <test.module>` equivalents).

## Enabling

Create `src/test/java/module-info.java` alongside the existing `src/main/java/module-info.java`. Both plugins auto-detect the file and set `settings.testModuleBuild = true`.

Auto-detection runs lazily — on the first plugin method invocation rather than at plugin construction — so layout overrides in `project.latte` are honored. For example:

```groovy
java.layout.mainSourceDirectory = Paths.get("src/java")
java.layout.testSourceDirectory = Paths.get("src/test")
// Auto-detect uses the updated paths when the first java.xxx() method runs.
```

To force a mode explicitly regardless of what's on disk:

```groovy
java.settings.moduleBuild = true
java.settings.testModuleBuild = false    // keep patch-module mode even if src/test/java/module-info.java exists
```

Explicit `true`/`false` values are preserved; only `null` triggers auto-detect.

## Writing the test module-info.java

The test module must `requires` every module it uses — main module, test framework(s), and any supporting libraries. Test classes also need to live in **different packages** from the main module (JPMS forbids two modules from declaring the same package), and each test package must be `opens`ed to the test framework so reflection-driven test discovery works.

Example for a main module named `org.example.foo` containing package `org.example.foo`:

```java
module org.example.foo.tests {
  requires org.example.foo;      // access the exported public API
  requires org.testng;           // test framework
  requires org.easymock;         // if using EasyMock
  // requires static org.slf4j;  // compile-time-only deps

  opens org.example.foo.tests to org.testng;
}
```

Test classes go under `src/test/java/org/example/foo/tests/` — note the `.tests` suffix. Trying to put them in `org.example.foo` will fail with:

```
package exists in another module: org.example.foo
```

## Automatic module names

Java 9+ JARs without a `module-info.class` are usable as **automatic modules**. Their module name comes from (in priority order):

1. The `Automatic-Module-Name` manifest attribute, if present.
2. Otherwise, derived from the JAR filename by stripping the version and replacing non-alphanumeric characters with `.`.

Common test-framework module names:

| Library | Automatic module name |
|---|---|
| TestNG 7+ | `org.testng` (from manifest) |
| EasyMock 5+ | `org.easymock` (from manifest) |
| jcommander | `jcommander` (derived from filename, not stable across rebrandings) |
| slf4j-api | `org.slf4j` (from manifest) |

If you see `module not found: <name>` at compile time, inspect the JAR:

```bash
jar --file path/to/library.jar --describe-module
# or
unzip -p path/to/library.jar META-INF/MANIFEST.MF | grep -i module
```

## Resource loading

Test classes often load resources via `ClassLoader.getResourceAsStream(...)`. Under JPMS this only works if the resource's package is `opens` or `exports` in the owning module. A resource at `src/test/resources/config.properties` ends up in the test JAR's **root** — no package — and is always accessible. A resource at `src/test/resources/org/example/foo/tests/fixture.json` is in the `org.example.foo.tests` package, which you'll already have declared `opens` for test discovery, so no extra configuration is needed. Mismatches surface as `InputStream` = null, not as exceptions; when tests start reading resources that weren't there before, double-check the `opens` directives.

## Runtime execution (java-testng)

When `testModuleBuild` is true, the plugin runs tests with:

```
java --module-path <all-deps-and-jars> \
     --add-modules ALL-MODULE-PATH,<testModuleName> \
     --module org.testng/org.testng.TestNG ...
```

`ALL-MODULE-PATH` forces resolution of every JAR on the module path, which is necessary because TestNG's transitive dependencies (slf4j-api, jcommander, etc.) are automatic modules that aren't explicitly `requires`'d by anything. Without it, they wouldn't be resolved and TestNG would throw `NoClassDefFoundError` at startup.

The `--module org.testng/org.testng.TestNG` entry point depends on TestNG shipping `Automatic-Module-Name: org.testng`. TestNG 7.0+ satisfies this. If you pin to an older TestNG, separate-test-module mode will not work — stay on patch-module mode or upgrade.

## When to stay in patch-module mode

- Testing package-private or non-exported code.
- Using legacy JARs that don't have automatic-module-friendly names.
- Monolithic projects where the main module's package tree doubles as the test package tree.

Patch-module mode is still the default behavior when only `src/main/java/module-info.java` exists (without a matching `src/test/java/module-info.java`). It is not deprecated.

## Related

- Design spec: [`docs/superpowers/specs/2026-04-17-separate-test-module-design.md`](superpowers/specs/2026-04-17-separate-test-module-design.md)
- Implementation plan: [`docs/superpowers/plans/2026-04-17-separate-test-module.md`](superpowers/plans/2026-04-17-separate-test-module.md)
