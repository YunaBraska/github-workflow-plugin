# UX/DX Gaps

## UX

- Remote action resolution uses public GitHub plus GitHub Enterprise accounts from JetBrains GitHub settings. There is
  no plugin-owned server settings UI.
- Unresolved remote actions now mention common failure modes directly in the editor, including account access, private
  repository permissions, rate limits, missing refs, and missing metadata. Per-status diagnostics are still missing.
- Cache controls are exposed as IDE actions discoverable through Find Action and, where the classic Tools menu is
  visible, under `Tools > GitHub Workflow`. A richer cache inspector is still missing.
- Suppressed action/input/output warnings can be restored through the `Restore Action Warnings` IDE action. A detailed
  suppression review panel is still missing.
- Link navigation now covers resolved local/remote `uses` from github.com and GitHub Enterprise, job IDs, `needs`,
  inputs, secrets, envs, matrix keys,
  job service IDs/ports, step output references, and reusable workflow job outputs where a local target exists. Remote
  metadata internals still need explicit data sources.
- Quick documentation now covers resolved `uses`, workflow/action parameters, expression variables, and common context
  segments. The remaining gap is richer rendered Markdown from remote action README files.
- Workflow execution UX is still missing. The preferred shape is a GitHub Workflow run configuration type with gutter
  play actions for runnable workflow files, an inputs editor for `workflow_dispatch`, Run tool window output, status
  polling, and Stop mapped to canceling the remote workflow run.
- Plugin/action/cache-control labels and notifications use resource bundles with top-20 locale files. Most editor
  inspection, quick-fix, and documentation strings still need extraction before localization can be called complete.
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
- UI testing is not configured. The stale UI workflow was removed instead of pretending it worked. Harsh, but fair.
