# Project Navigation

The plugin is intentionally plain Java with Gradle wrapper entrypoints. The useful local commands are:

- `./gradlew test`
- `./gradlew performanceTest`
- `./gradlew verifyPlugin buildPlugin`
- `./gradlew runIde`

## Runtime Entry Points

- `CodeCompletion` handles workflow expressions, `uses`, `with`, secrets, shell values, local files, remote action refs,
  and GitHub context/default environment completions.
- `HighlightAnnotator` handles editor diagnostics, symbol coloring, quick fixes, action update suggestions, and
  variable/run output highlighting.
- `ReferenceContributor` handles local PSI references and remote web references.
- `WorkflowDocumentationProvider` handles hover and quick documentation.
- `WorkflowRunLanguageInjector` injects shell-like languages into `run` blocks based on `shell`.
- `WorkflowRunConfigurationType` and related workflow-run classes handle workflow dispatch from the IDE Run tool window.
- `GitHubRequestAuthorizations` centralizes GitHub account, optional token-env fallback, and anonymous request ordering.
- `GitHubActionCache`, `RemoteActionProviders`, and `RemoteServerSettings` resolve and cache local/remote action and
  reusable workflow metadata.
- `GitHubWorkflowSettingsConfigurable` exposes the plugin settings page for language override, cache review/delete,
  cache import/export, plugin cache size, and the tiny support button with suspicious amounts of caffeine energy.
- `WorkflowRunLogRenderer` compacts GitHub Actions logs into named blocks with `0001 |` line numbers, `run:` command
  lines, ANSI cleanup, and warning/error classification for Run tool-window job consoles.

## Tests

Editor behavior should be tested through IntelliJ fixture entrypoints. See:

- `doc/adr/0008-test-through-editor-and-runtime-boundaries.md`
- `doc/spec/editor-test-matrix.md`

Remote GitHub behavior should use fake HTTP servers or explicit client boundaries. Network access in tests is guilty
until proven innocent.
