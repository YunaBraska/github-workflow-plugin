# 0004 Use Actions for Cache Controls and Resource Bundles

## Status

Accepted

## Context

Resolved remote action and reusable workflow metadata should work offline after it has been fetched once. Users also
need a predictable way to clear or refresh that cache when metadata is stale.

The project should avoid another settings page unless it carries real configuration. The current server source is already
JetBrains GitHub/GitHub Enterprise accounts plus public github.com fallback.

JetBrains action-system documentation supports action/group text from resource bundles, including dedicated bundles on
`<actions>`.

## Decision

Add `Tools > GitHub Workflow` actions for refreshing resolved remote metadata and clearing cached metadata.

Keep cache operation logic in `GitHubActionCache` and keep action classes thin.

Use `messages.GitHubWorkflowBundle` for plugin action labels, descriptions, and cache notifications. Add locale files
for the first localization pass and test that every locale has the same nonblank keys as the default bundle.

## Consequences

Users get explicit cache controls without a plugin-owned settings menu.

The cache can be tested through public service methods and action registration can be tested through the IntelliJ action
system.

This does not fully localize editor diagnostics, quick fixes, or documentation text yet. Those strings must be extracted
in later passes instead of pretending partial translation is complete.
