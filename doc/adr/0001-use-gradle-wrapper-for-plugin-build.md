# 0001 Use Gradle Wrapper for Plugin Build

## Status

Accepted

## Context

The project is an IntelliJ Platform plugin. Local setup was hard because older tooling required a compatible runtime and
the CI workflow used stale IntelliJ plugin tasks.

Maven was considered because it is boring for many Java projects. For IntelliJ Platform plugins, the boring path is the
official IntelliJ Platform Gradle Plugin: it wires IDE dependencies, bundled plugins, instrumentation, sandbox runs,
plugin packaging, signing, publishing, and Plugin Verifier tasks.

## Decision

Keep the Gradle wrapper as the build entrypoint and use a plain Groovy `build.gradle`.

Use Java 25 to run Gradle and compile plugin bytecode with `--release 21`, matching the 2024.3 baseline while keeping the
developer runtime current.

Use `verifyPlugin` as a required compatibility gate and fail on deprecated, internal, scheduled-for-removal, invalid
plugin, missing dependency, and compatibility verifier findings.

## Consequences

Local setup is three commands or fewer and no longer requires pointing the project at a local JetBrains JDK.

The build remains Java-first and avoids Kotlin project code and Kotlin build scripts.

Moving to Maven is rejected for now because it would either lose official JetBrains build behavior or require rebuilding
that behavior manually. That is not boring. That is paperwork wearing a mask.
