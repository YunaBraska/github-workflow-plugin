# 0006 Use Generated GitHub Docs Data Snapshots

## Status

Accepted

## Context

GitHub context keys and default environment variables change independently of the plugin. Keeping those values as Java
maps made completion, highlighting, and documentation drift easy.

## Decision

Store GitHub context and default environment metadata as checked-in TSV snapshots under `src/main/resources/github-docs`.
Refresh them with `./gradlew generateGitHubDocsData`, which reads the rendered official GitHub Docs tables. Runtime code
loads the snapshots through one path, and tests assert the exposed maps match the generated resources exactly.

## Consequences

The plugin does not need network access during normal build, test, or runtime. Updating to new GitHub docs is an explicit
maintainer action, and stale snapshots are visible in review instead of hidden in Java code.
