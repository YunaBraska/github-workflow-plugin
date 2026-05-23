# 0007 Use Date-Based SemVer and 2024.2 Baseline

## Status

Accepted

## Context

The previous plugin version looked like an IntelliJ platform version. That made it unclear whether `2024.3.0` meant the
plugin release, the IDE baseline, or stale project state.

JetBrains Marketplace requires plugin versions to follow semantic versioning. Slashes are not valid for that, so the
date-shaped release version must use dot-separated numeric identifiers.

The lowest supported platform is currently 2024.2 / branch `242`. JetBrains lists 2024.2 as Java 21-based, which
matches the current bytecode target and keeps backward compatibility without carrying older Java 17-era platform
constraints. The plugin verifier passed against 2024.2, 2024.3, 2025.x, 2026.1, and 2026.2 EAP with this baseline.

## Decision

Use `YYYY.M.D` plugin versions and `vYYYY.M.D` tags.

Keep `pluginSinceBuild = 242` until a verifier-clean feature needs APIs newer than 2024.2. When that happens, raise the
baseline deliberately and document the user-visible compatibility cost.

The tag workflow updates `gradle.properties`, commits that version with the Kira bot identity, tags that commit, and
pushes both commit and tag.

## Consequences

Release versions are obvious, sortable enough for this project, and still compatible with Marketplace SemVer rules.

Only one release per day is supported by the default format. If same-day patch releases become necessary, this ADR must
be revisited before adding suffixes.
