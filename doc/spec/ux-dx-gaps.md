# UX/DX Gaps

## UX

- Remote action resolution uses public GitHub plus GitHub Enterprise accounts from JetBrains GitHub settings. Tokens are
  only sent to matching GitHub hosts, and anonymous access is the fallback. There is no plugin-owned server settings UI.
- Unresolved remote actions now mention common failure modes directly in the editor, including account access, private
  repository permissions, rate limits, missing refs, and missing metadata. Workflow dispatch failures report 401/rate
  limit failures with a GitHub settings hint. Per-status editor diagnostics for remote metadata are still missing.
- Cache controls are exposed as IDE actions discoverable through Find Action and, where the classic Tools menu is
  visible, under `Tools > GitHub Workflow`. Settings also expose a cache inspector with per-entry review, selected/all
  deletion, import/export, and plugin cache size.
- Workflow run logs use one Run tool-window content tab with a JUnit-style execution tree and a detail console so jobs,
  timings, warnings, and failures read like normal IDE runs instead of a tab parade.
- Suppressed action/input/output warnings can be restored through the `Restore Action Warnings` IDE action. A detailed
  suppression review panel is still missing.
- Link navigation now covers resolved local/remote `uses` from github.com and GitHub Enterprise, job IDs, `needs`,
  inputs, secrets, envs, matrix keys,
  job service IDs/ports, step output references, and reusable workflow job outputs where a local target exists. Remote
  metadata internals still need explicit data sources.
- Quick documentation now covers resolved `uses`, workflow/action parameters, expression variables, and common context
  segments. The remaining gap is richer rendered Markdown from remote action README files.
- Workflow execution now has a first Run Configuration implementation for `workflow_dispatch`: context-created GitHub
  Workflow configurations defaulting to the current Git branch, a gutter play/stop action on `workflow_dispatch`,
  `key=value` dispatch inputs, account-first authentication through JetBrains GitHub settings with successful-token reuse
  during polling, environment-token fallback before anonymous access, Run tool window status output, one JUnit-style
  workflow tree with grouped jobs, selected-node log output, job URLs in the detail console, a thin progress bar, elapsed
  job timing, and Stop mapped to canceling the remote workflow run. The workflow tree uses test-style status icons and
  compact GitHub timestamps/log commands into named blocks with four-digit line numbers, `run:` command lines, ANSI
  cleanup, and warning/error console colors.
  Remaining execution UX gaps are richer input widgets for typed choices/booleans, passive run lists for workflows
  triggered by non-dispatch events, historical duration estimates, and deeper GitHub-style log rendering such as step
  folding and clickable SHAs.
- Plugin/action/cache-control labels, settings, run-console status text, issue reporting, editor inspections, quick fixes,
  completion detail text, and workflow documentation labels use resource bundles with top-20 locale files. Remaining
  localization work is mostly native translation review for newly added fallback-English keys.
- Highlighting, references, styling, documentation, and completion now have focused fixture tests through YAML editor
  entrypoints. GitHub context/default-env metadata is generated from checked-in documentation snapshots.

## DX

- Schema updates should be a separate manual task, not a test side effect.
- Verifier reports are now generated, but release decisions still need a documented review checklist.
- JaCoCo reporting is present but currently not reliable with the IntelliJ fixture/runtime class loading; coverage gates
  stay off until that is made truthful.
- Editor fixture coverage has been split into isolated highlighting, reference, and completion tests. The remaining
  matrix is tracked in [Editor Test Matrix](editor-test-matrix.md).
- Bounded large-workflow highlighting performance coverage now runs through `./gradlew performanceTest`.
- A fake HTTP server covers remote metadata behavior deterministically. Remote reload gutter execution has a resolver
  boundary and no-sleep test coverage.
- The release workflow builds, verifies, packages, and uploads the plugin zip to a GitHub release. Disposable tag/release
  tests have passed; production release behavior still needs the first real main-branch release.
- Tag and GitHub release workflows are main-branch/tag gated. Marketplace publishing/signing is wired to published
  releases and should stay dry-run/manual until repository secrets are confirmed.
- The tag workflow now creates a date-based plugin version commit with Kira bot identity, tags that commit, and pushes
  both commit and tag.
- UI testing is not configured. The stale UI workflow was removed instead of pretending it worked. Harsh, but fair.
