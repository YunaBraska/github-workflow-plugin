# Project Navigation

The plugin is intentionally plain Java with Gradle wrapper entrypoints. The useful local commands are:

- `./gradlew test`
- `./gradlew performanceTest`
- `./gradlew verifyPlugin buildPlugin`
- `./gradlew runIde`

## Runtime Entry Points

- `WorkflowLocation.from(PsiElement)` is the shared PSI/YAML location for workflow keys, paths, files, repositories,
  and branches.
- `WorkflowSyntax` owns workflow syntax completions, validation metadata, JSON schema hookup, file icons, and `run`
  language injection.
- `WorkflowReferences` owns local PSI references, remote web references, and expression reference targets.
- `GitHubActionCache` is the cache boundary for action/reusable-workflow metadata, cache actions, warning restore, and
  startup refresh.
- `WorkflowRun` is the remote workflow-run boundary: dispatch, cancel, rerun, delete, jobs, logs, artifacts, and branch
  resolution.
- `WorkflowCompletion` handles workflow expressions, `uses`, `with`, secrets, shell values, local files, remote action refs,
  and GitHub context/default environment completions through `WorkflowSyntax`, `WorkflowLocation`, and
  `GitHubActionCache`.
- `WorkflowAnnotator` handles editor diagnostics, symbol coloring, quick fixes, action update suggestions, and
  variable/run output highlighting.
- `WorkflowDocumentationProvider` handles hover and quick documentation.
- `RemoteActionProviders` centralizes GitHub account, enterprise account, optional token-env fallback, anonymous request
  ordering, and remote server settings.
- `WorkflowRunConfiguration` handles workflow dispatch from the IDE Run tool window.
- `GitHubWorkflowSettingsConfigurable` exposes the plugin settings page for language override, cache review/delete,
  cache import/export, plugin cache size, and the tiny support button with suspicious amounts of caffeine energy.
- `WorkflowRunView.LogRenderer` compacts GitHub Actions logs into named blocks with `0001 |` line numbers,
  `run:` command lines, ANSI cleanup, and warning/error classification for Run tool-window job consoles.

## Tests

Editor behavior should be tested through IntelliJ fixture entrypoints. See:

- `doc/adr/0008-test-through-editor-and-runtime-boundaries.md`
- `doc/spec/editor-test-matrix.md`
- `doc/spec/gitea-github-actions-compatibility.md`

Remote GitHub behavior should use fake HTTP servers or explicit client boundaries. Network access in tests is guilty
until proven innocent.
