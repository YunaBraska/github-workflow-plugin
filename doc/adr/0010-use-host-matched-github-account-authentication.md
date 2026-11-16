# 0010 Use Host-Matched GitHub Account Authentication

## Status

Accepted

## Context

Private repositories, API rate limits, and workflow runs require GitHub authentication. The IDE's internal token API
is not supported for plugins, while its public request executor does not expose all response details required by the
workflow client. Account credentials must not be sent to another server.

## Decision

The plugin manages its own GitHub accounts and stores their personal access tokens in the IDE password store. Existing
JetBrains GitHub accounts are not imported. For GitHub REST calls, try enabled plugin accounts matching the target
protocol, host, port, and API path, followed by the configured and default GitHub token environment variables, then
anonymous access. Several accounts may be configured for the same server. Environment tokens remain explicit fallbacks
for the configured target.

Workflow authentication errors link to the plugin's GitHub account settings. GitHub Enterprise servers are configured
in the same account settings.

## Consequences

Users migrating from JetBrains GitHub accounts must add their accounts and tokens again. The plugin keeps complete HTTP
response details and no longer depends on an internal JetBrains token API. Public repositories can still work without
credentials. A token in an environment variable may be sent to the configured target and must be suitable for that host.
