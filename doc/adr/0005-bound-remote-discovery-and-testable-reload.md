# 0005 Bound Remote Discovery and Testable Reload

## Status

Accepted

## Context

Completion should help before an action is fully resolved, but arbitrary remote discovery can become slow, noisy, or
rate-limit hostile.

Remote reload is triggered from editor gutter actions and used to perform network/file work in the background, which made
it hard to test without sleeping.

## Decision

Keep remote discovery bounded:

- Search matching repositories only when the user has typed an owner prefix such as `actions/`.
- Suggest at most 10 refs for a typed callable such as `actions/checkout@`.
- Prefer tags before branches for ref completion.
- Cache discovered refs in the action cache so later completion can work offline.

Add a resolver strategy boundary inside `GitHubActionCache` so remote reload gutter execution can be tested with a latch
instead of sleeps or real network calls.

## Consequences

Completion is useful before metadata resolution without turning every popup into an unbounded remote crawl.

The reload gutter action remains asynchronous in production and deterministic in tests.

Full marketplace-style search across all GitHub actions remains intentionally out of scope until the UX can rank results
without noise.
