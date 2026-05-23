# 0011 Use One Pipeline Job for CI and Release

## Status

Accepted

## Context

The release pipeline previously used separate build, tag, release, and Marketplace workflows. It also created several
GitHub Actions cache entries. That made the release path harder to reason about and let large caches crowd each other
out.

## Decision

Use one workflow file with one job and one manually managed cache key.

The same job handles all modes:

- branch and PR runs execute the normal test/package path;
- a `main` push that is not the generated release commit executes the release path;
- a manual dispatch executes the release path, with optional dry-run support.

The release path prepares the version, runs the full checks and Plugin Verifier, publishes the plugin ZIP to GitHub
Packages, uploads the same ZIP directly to JetBrains Marketplace, pushes the release commit and tag, and creates or
updates the GitHub release.

After a successful non-PR run, the job prunes every GitHub Actions cache entry except the current pipeline cache key.

## Consequences

- CI and release behavior live in one place.
- There is only one cache entry by design after a successful writable run.
- GitHub Packages and Marketplace publishing use the exact artifact attached to the GitHub release.
- Release publishing requires `PUBLISH_TOKEN`.
- The workflow has more conditional shell logic, but fewer moving GitHub Actions parts.
