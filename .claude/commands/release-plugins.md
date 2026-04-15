Release all Latte plugins in dependency order. Each plugin is updated, committed, pushed, and released before moving to the next.

## Steps

1. Ensure that the project (including all the plugins) has no uncommitted or un-pushed changes. If there are uncommitted or un-pushed changes, **stop immediately** and inform the user they need to manually commit and push their changes.

2. Read the CLI project version from `project.latte` in the repo root (the `version: "X.Y.Z"` in the project line)

3. For each plugin in this exact order:
   - dependency
   - file
   - groovy
   - groovy-testng
   - release-git
   - database
   - debian
   - idea
   - java
   - java-testng
   - linter
   - pom

   Do the following from the plugin's directory (`plugins/<name>/`):

   **a. Update the version** in `plugins/<name>/project.latte` to match the CLI version. The version is in the `project(...)` line, e.g. `version: "0.1.3"` → `version: "0.1.4"`. Use the Edit tool to make this change.

   **b. Run `latte upgrade dependencies`** to upgrade all dependencies to their latest versions.

   **c. Run `latte upgrade plugins`** to upgrade all loadPlugin references to their latest versions.

   **d. Commit and push** the changes to `plugins/<name>/project.latte`:
   ```
   git add plugins/<name>/project.latte
   git commit -m "<name> plugin: update to version <version>"
   git push
   ```
   **e. Run `latte release`** from the plugin directory to publish the release.

4. If any step fails for any plugin, **stop immediately** and report which plugin and step failed. Do not continue to the next plugin.

## Important

- All git commands should be run from the repo root directory.
- All latte commands (`latte upgrade dependencies`, `latte upgrade plugins`, `latte release`) must be run from the plugin directory (`plugins/<name>/`).
- Use a 5-minute timeout for `latte release` commands since they run tests.
- After all plugins are released successfully, report a summary of what was released.
