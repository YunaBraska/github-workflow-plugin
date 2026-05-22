# Contributing

When contributing to this repository, please first discuss the change you wish to make via issue, email, or any other method with the owners of this repository before making a change.

## Pull Request Checklist

1. Create your branch from `main`.
2. Keep changes boring: plain Java, Gradle wrapper, no new dependency unless it earns rent.
3. Add behavior-first tests through editor, run-configuration, cache, or client entrypoints.
4. Run `./gradlew test` before pushing. Run `./gradlew check verifyPlugin buildPlugin` before release work.
5. Do not bump `pluginVersion` by hand for release PRs; the tag workflow owns the date-based `YYYY.M.D` version bump.
6. Keep user-facing text in `src/main/resources/messages/GitHubWorkflowBundle*.properties`.
7. Update `README.md`, `doc/navigation.md`, ADRs, or `doc/spec/*` when behavior or workflow changes.
