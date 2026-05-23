# 0008 Test Through Editor and Runtime Boundaries

## Status

Accepted

## Context

The plugin used to be hard to test because behavior was spread across IntelliJ editor callbacks, cache state, remote
metadata, and YAML PSI traversal.

Testing private helpers directly would make refactors painful and still miss the actual IDE behavior users see.

## Decision

Prefer public entrypoints:

- Use IntelliJ fixture tests for completion, highlighting, references, quick documentation, quick fixes, gutter actions,
  injected languages, and run configuration registration.
- Use fake HTTP servers or injectable transport boundaries for GitHub API behavior.
- Use dedicated plain unit tests only for boundary parsers such as release input parsing and repository URL resolution.
- Keep every meaningful case in its own test method unless the behavior is genuinely one scenario.
- Add a failing-first regression test for each fixed issue.
- Keep performance coverage in the dedicated `performanceTest` task instead of hiding timing assumptions in normal unit
  tests.

## Consequences

Tests are slower than isolated helper tests, but they protect user-visible behavior and reduce false confidence.

Helper code may still be refactored freely as long as the editor/runtime behavior stays stable. Good. Less ceremony,
more damage containment.
