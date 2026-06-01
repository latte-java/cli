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

   Do the following from the plugin's directory (`cd plugins/<name>/`):

   **a. Update the version** in `plugins/<name>/project.latte` to match the CLI version. The version is in the `project(...)` line, e.g. `version: "0.1.3"` → `version: "0.1.4"`. Use the Edit tool to make this change. If the version is already the same as the CLI version, skip this step.

   **b. Check if the plugin was already released** by checking for a git tag in the format `<plugin-name>-<version>` and checking the Latte repository search API at `https://api.lattejava.org/api/v1/repository/search?id=org.lattejava.plugin:<plugin-name>&latest=true`. The API response returns JSON that contains the latest version of the artifact in the repository in the "versions" property. The Git tag and the artifact must both exist for the plugin to be considered released. If the plugin was already released, skip to the next plugin.

   **b. Run `latte upgrade dependencies`** to upgrade all dependencies to their latest versions.

   **c. Run `latte upgrade plugins`** to upgrade all loadPlugin references to their latest versions.

   **d. Commit and push** the changes to `project.latte`:
   ```
   git add project.latte
   git commit -m "chore: <name> plugin version updated to <version>"
   git push
   ```
   **e. Run `latte release`** from the plugin directory to publish the release.

4. If any step fails for any plugin, **stop immediately** and report which plugin and step failed. Do not continue to the next plugin.

## Important

- All `git` and `latte` commands should be run from the plugin directory (`plugins/<name>/`).
- All latte commands (`latte upgrade dependencies`, `latte upgrade plugins`, `latte release`) must be run from the plugin directory (`plugins/<name>/`).
- Use a 5-minute timeout for `latte release` commands since they run tests.
- After all plugins are released successfully, report a summary of what was released.
