# Plugin Size Refactor Plan

## Goal

Reduce plugin size and production-code duplication while keeping the current feature set and public behavior covered by
tests.

This is not a feature rollback. The target is a smaller, calmer implementation: same teeth, less boilerplate jaw.

## Marketplace Facts

| Version | Status | Date | Compatibility Range | Size | Uploaded By |
| --- | --- | --- | --- | --- | --- |
| 2025.0.0 | Approved | 21 Apr 2025 | 251.0+ | 150.58 KB | Yuna Morgenstern |
| 2026.5.23 | Under review | 23 May 2026 | 242.0+ | 620.83 KB | Yuna Morgenstern |

## Current Local Size Shape

The 2026 line grew for real reasons: workflow run UI, broader completion/highlighting/validation, remote metadata, cache
controls, and top-20 localization.

The largest cleanup candidates are:

- Completion, validation, documentation, highlighting, and references each walk similar workflow structure.
- `CodeCompletion` and `WorkflowRunConsoleTabs` are large coordination classes.
- Resource bundles repeat many full strings across all locale files.
- Workflow syntax metadata is split across schemas, generated docs snapshots, hard-coded maps, and local presentation
  code.

## Refactor Direction

1. Build one immutable `WorkflowModel` from the YAML PSI.
2. Feed completion, validation, references, hover docs, line markers, and highlighting from that model.
3. Move workflow syntax keys, values, descriptions, and validation rules into one data-driven registry.
4. Split workflow run UI into small model, tree state, renderer, toolbar action, and log view classes.
5. Reduce localization duplication by keeping the base bundle authoritative and only overriding translated values.
6. Keep schemas and docs snapshots, but avoid duplicating their meaning in Java maps when generated data can serve it.

## Expected Reduction

Realistic target:

- 25-35% less production Java code.
- 150-250 KB smaller Marketplace artifact if localization and duplicated syntax metadata are cleaned up.
- Final artifact target: 350-450 KB while keeping current behavior.

Returning to roughly 150 KB is not realistic without dropping current functionality or broad localization.

## Guardrails

- Keep current tests green.
- Add regression tests before changing shared completion, validation, highlighting, or workflow-run behavior.
- Prefer public editor/runtime entrypoints over private helper tests.
- Do not change user-facing behavior unless the test or spec says the old behavior was wrong.
- No speculative abstractions. If a shared model does not simplify at least two consumers, delete it.

## Done Criteria

- Marketplace artifact size is measured before and after.
- Production Java line count is measured before and after.
- Existing editor and workflow-run tests pass.
- Manual IDE smoke test covers completion, validation, quick fixes, left ruler markers, and workflow run tree.
- Release notes mention user-visible polish only, not internal surgery.
