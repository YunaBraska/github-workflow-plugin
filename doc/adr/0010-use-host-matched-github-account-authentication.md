# 0010 Use Host-Matched GitHub Account Authentication

## Status

Accepted

## Context

Remote metadata and workflow dispatch calls need authentication for private repositories, higher rate limits, and
write APIs such as `workflow_dispatch`. The plugin already depends on JetBrains' GitHub integration, so a separate token
store or server UI would duplicate IDE settings and create more security surface.

Tokens must not be sent to unrelated hosts. A github.com token must not be used for a GitHub Enterprise host, and an
Enterprise token must not be sent to github.com.

## Decision

For GitHub REST calls, use this order:

1. GitHub accounts from JetBrains settings whose server host matches the request API host.
2. Optional token environment variable configured on the run configuration or remote server test fixture.
3. Anonymous request.

For ambiguous remote action references, prefer github.com servers before Enterprise servers. For repository-derived
workflow runs, infer the API host from the Git remote and use matching accounts first.

Authentication and rate-limit workflow dispatch failures must include a direct hint to `Settings > Version Control >
GitHub` and show a notification action that opens GitHub settings.

## Consequences

Public repositories still work without accounts where GitHub allows it. Authenticated dispatch uses the IDE account
instead of forcing a `GH_TOKEN` environment variable. Failed or missing accounts degrade predictably instead of throwing
an unexplained 401 at users.
