# 0009 Use Run Configurations for Workflow Execution

## Status

Accepted

## Context

Users need to trigger workflows, see whether a run is queued or running, stop it, and inspect logs without leaving the
IDE. A loose toolbar action would be easy to add but would not fit normal IntelliJ run/debug UX.

GitHub only supports manual dispatch for workflows that declare `workflow_dispatch`. Other workflow events can be
observed and rerun through GitHub APIs, but they cannot honestly be triggered as arbitrary event simulations.

## Decision

Add a `GitHub Workflow` Run Configuration type.

Create configurations from workflow YAML context, show a gutter play action on `workflow_dispatch`, expose dispatch
inputs as `key=value` lines, print status/log output in the Run tool window, and map Stop to GitHub's cancel-run API.
Represent GitHub jobs in one Run tool-window tree with a selected-job detail console instead of creating one tab per
job, so the UI stays close to normal IntelliJ execution views without pretending to be the built-in test runner.

Use the repository remote to infer owner/repo/API URL. Use JetBrains GitHub accounts first, an optional token
environment variable second, and anonymous access last. Never send a token to a different GitHub host than the account
belongs to. Public read calls may work without a token; dispatch/cancel generally need an authenticated account.

## Consequences

Workflow execution behaves like a normal IDE run target instead of a custom panel.

The first implementation supports `workflow_dispatch`. Broader "show running workflows for any event" UX can build on
the same client and Run tool window boundary without faking unsupported GitHub triggers.
