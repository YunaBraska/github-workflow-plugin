# 0002 Use JetBrains GitHub Accounts for Remote Action Providers

## Status

Accepted

## Context

Remote action metadata used to assume github.com URL shapes. That made GitHub Enterprise installs hard to support and
hard to test without live network calls.

## Decision

Use GitHub/GitHub Enterprise accounts already registered in JetBrains settings as GitHub remote server sources. Always
include public github.com as a fallback source.

Do not add a plugin-owned server settings UI. Self-hosted GitHub belongs in `Version Control > GitHub > Log in to GitHub
Enterprise`, which already exists in the IDE.

Resolve remote metadata through the configured server APIs first, then fall back to public GitHub. Use the repository
contents API for `action.yml`, fallback `action.yaml`, and reusable workflow metadata. The plugin does not persist
tokens.

Use a JDK fake HTTP server in tests for GitHub Enterprise-shaped API behavior. Tests may inject temporary server
definitions directly into the service; that is not a user-facing settings surface.

## Consequences

Self-hosted GitHub action metadata can be resolved, linked, highlighted, styled, documented, and completed without
contacting public GitHub in tests.

The plugin stays boring: no duplicate account UI, no token storage, and fewer settings to test.

The provider currently resolves metadata and refs after a callable is known. It does not browse arbitrary remote
repositories to discover unknown action/workflow slugs.
