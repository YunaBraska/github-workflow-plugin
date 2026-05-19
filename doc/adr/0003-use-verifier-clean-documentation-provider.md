# 0003 Use Verifier-Clean Documentation Provider

## Status

Accepted

## Context

Hover documentation is needed for resolved actions, workflow variables, action inputs, secrets, and outputs.

JetBrains documents the Documentation Target API as the current path for 2023.1 and newer IDEs. In the current verifier
matrix, that API still reports required `Pointer` and presentation types as experimental API usages. The project treats
clean Plugin Verifier output as a release gate.

The older documentation provider extension is still supported by the platform and does not trigger verifier warnings in
the supported matrix. "Older" here means boring and stable, not broken.

## Decision

Use the verifier-clean documentation provider extension for workflow hover/quick documentation until the Documentation
Target API no longer produces verifier warnings across the supported IDE matrix.

Register the provider before YAML JSON schema documentation so workflow-specific hovers for `uses`, variables, inputs,
outputs, and jobs win over generic schema text such as `uses: string`.

Keep the implementation behind one provider class and test it through IntelliJ documentation entrypoints so it can be
swapped to the target API later without changing user-facing behavior.

## Consequences

The plugin keeps hover UX for resolved workflow symbols while Plugin Verifier can remain a release gate.

The provider intentionally returns `null` for unsupported documentation targets because that is the IntelliJ Platform
documentation API contract.

Revisit this decision when the supported JetBrains versions expose a warning-free Documentation Target API.
