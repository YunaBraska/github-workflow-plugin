# 0010 Use Host-Matched GitHub Account Authentication

## Status

Accepted

## Context

Private repositories, API rate limits, and workflow runs require GitHub authentication. The IDE's internal token API
is not supported for plugins, while its public request executor does not expose all response details required by the
workflow client. Account credentials must not be sent to another server.

## Decision

The plugin manages its own GitHub accounts and stores their credentials in the IDE password store. Existing
JetBrains GitHub accounts are not imported. For GitHub REST calls, try enabled plugin accounts matching the target
protocol, host, port, and API path, followed by the configured and default GitHub token environment variables, then
anonymous access. Several accounts may be configured for the same server. Environment tokens remain explicit fallbacks
for the configured target.

GitHub.com supports the GitHub App device flow and personal access tokens. The device flow uses no client secret and
stores the access and refresh tokens together in the password store. Background requests refresh expiring tokens once
per account and do not restore credentials removed while a refresh is in progress. GitHub Enterprise uses personal
access tokens. Workflow authentication errors link to the plugin's GitHub account settings. JetBrains' GitHub plugin is
not required; its authoring features remain independent when it is enabled.

## Consequences

Users migrating from JetBrains GitHub accounts must add their accounts and tokens again. The plugin keeps complete HTTP
response details and no longer depends on an internal JetBrains token API. Public repositories can still work without
credentials. A token in an environment variable may be sent to the configured target and must be suitable for that host.
GitHub App user tokens can access only repositories available to both the user and the app installation. Users must
install the app on the repositories they want to access.
