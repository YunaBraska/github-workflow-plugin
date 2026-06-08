# Project Navigation

The plugin is intentionally plain Java with Gradle wrapper entrypoints. The useful local commands are:

- `./gradlew test`
- `./gradlew performanceTest`
- `./gradlew verifyPlugin buildPlugin`
- `./gradlew runIde`

## Runtime Entry Points

- `entry/CodeCompletion` handles workflow expressions, `uses`, `with`, secrets, shell values, local files, remote action refs,
  and GitHub context/default environment completions.
- `entry/HighlightAnnotator` handles editor diagnostics, symbol coloring, quick fixes, action update suggestions, and
  variable/run output highlighting.
- `entry/ReferenceContributor` handles local PSI references and remote web references.
- `entry/WorkflowDocumentationProvider` handles hover and quick documentation.
- `entry/WorkflowRunLanguageInjector` injects shell-like languages into `run` blocks based on `shell`.
- `entry/WorkflowRunConfigurationType` and `run/*` handle workflow dispatch from the IDE Run tool window.
- `client/GitHubRequestAuthorizations` centralizes GitHub account, optional token-env fallback, and anonymous request ordering.
- `state/GitHubActionCache`, `client/RemoteActionProviders`, and `state/RemoteServerSettings` resolve and cache local/remote action and
  reusable workflow metadata.
- `settings/GitHubWorkflowSettingsConfigurable` exposes the plugin settings page for language override, cache review/delete,
  cache import/export, plugin cache size, and the tiny support button with suspicious amounts of caffeine energy.
- `run/WorkflowRunLogRenderer` compacts GitHub Actions logs into named blocks with `0001 |` line numbers, `run:` command
  lines, ANSI cleanup, and warning/error classification for Run tool-window job consoles.

## Package Shape

- `entry`: IntelliJ extension triggers and UI entry points.
- `syntax`: workflow key/value metadata shared by completion, validation, documentation, highlighting, and references.
- `git`: repository and branch discovery.
- `client`: remote GitHub-compatible HTTP boundaries.
- `run`: workflow dispatch, run tree, downloads, and log rendering.
- `state`: persistent IDE services and cache state.
- `settings`: settings UI.
- `i18n`: message bundle access.

## Tests

Editor behavior should be tested through IntelliJ fixture entrypoints. See:

- `doc/adr/0008-test-through-editor-and-runtime-boundaries.md`
- `doc/spec/editor-test-matrix.md`

Remote GitHub behavior should use fake HTTP servers or explicit client boundaries. Network access in tests is guilty
until proven innocent.
