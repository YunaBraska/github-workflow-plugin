# GitHub Workflow Plugin

*Your Ultimate Wingman for GitHub Workflows and Actions! 🚀*

![Build](https://github.com/YunaBraska/github-workflow-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/YunaBraska)
[![](https://img.shields.io/static/v1?label=DataPrivacy&message=%F0%9F%94%92&logo=springsecurity&color=%#6DB33F)](doc/DataPrivacy.md)

## Development Is Active Again

After a long pause, this plugin is back in active development. The project now has a plain Java/Gradle setup, a much
larger editor test suite, refreshed release automation, and compatibility checks for current JetBrains IDE builds.

Contributors are still welcome. The difference now: the floor is sturdier, the lights are on, and the CI is expected to
complain before users do.

---

## Why Choose GitHub Workflow Plugin?

<!-- Plugin description -->
Say goodbye to tedious trial and error! Experience seamless GitHub workflow management like never before. Create
pipelines at the speed of thought with this JetBrains plugin that extends support for GitHub Actions and Workflows.
_[See Screenshots](https://plugins.jetbrains.com/plugin/21396-github-workflow)_

## Key Features

* 🌈 Autocomplete & Syntax Highlighting: Write workflow YAML files with confidence. Autocomplete suggestions and clear
  syntax highlighting will make your code look and feel pristine.
* 🚀 Repository Access: Integrate with your private GitHub repositories for those secret projects you're working on.
* 🏢 Self-hosted GitHub: Resolve metadata from public GitHub and GitHub Enterprise accounts already configured in
  JetBrains settings, without storing plaintext tokens in this plugin.
* 🧹 Cache Controls: Refresh, inspect, export, import, or clear resolved action/workflow metadata from Find Action,
  `Tools > GitHub Workflow`, or `Settings > Tools > GitHub Workflow`.
* ▶️ Workflow Runs: Create a GitHub Workflow Run Configuration from `workflow_dispatch`, default to the current branch,
  provide inputs, follow job progress in a Run tool-window tree, inspect selected job logs, and stop remote runs through
  the IDE or the `workflow_dispatch` gutter action. Job nodes use test-result style status icons and color warnings/errors in logs.
  GitHub log groups render as named blocks with stable `0001 |` line numbers, `run:` command lines, and stripped ANSI
  noise.
* ⬆️ Action Updates: Resolved major-version action refs such as `actions/checkout@v3` can offer a quick fix when newer
  cached refs such as `v4` are available.
* 🗺️ Local Path Resolution: Navigate effortlessly with one-click access to local paths.
* ✅ Validation Engine: Validates linked local actions and workflows, but hey, you can turn this off too.
* 🛡️ Security: We respect your privacy! The plugin doesn't use or store your personal data; it only accesses remote
  actions and workflows when necessary.
* 🧩 Extensive Schema Support: Covers Depentabot, Actions, Workflows, Founding, Issue Config, Issue Forms, and Workflow
  Templates for comprehensive project management.

## Getting Started

* **Installation**: Download the plugin
  from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/21396-github-workflow).
* **Configuration**: Add GitHub or GitHub Enterprise accounts via `File > Settings > Version Control > GitHub`. The
  plugin does not add a second server settings screen.
* **Cache**: Use Find Action (`Shift` twice) for `Refresh Action Cache`, `Clear Action Cache`, or `Restore Action
  Warnings`. IDEs with the classic Tools menu also show these under `Tools > GitHub Workflow`. For review/delete,
  import/export, plugin cache size, and language override, use
  `Settings > Tools > GitHub Workflow`.
* **Workflow Runs**: Add GitHub accounts in `File > Settings > Version Control > GitHub`, then use the gutter play
  action on `workflow_dispatch` or create a `GitHub Workflow` Run Configuration. GitHub jobs appear in one Run tree with
  selected-node log output.
  Context-created runs default to the checked-out Git branch and print clickable workflow/job URLs where GitHub exposes
  them. The gutter play action is shown only when the workflow file belongs to a resolvable GitHub repository. Runs try
  matching IDE accounts first, then other IDE GitHub accounts, then `GITHUB_TOKEN`, `GH_TOKEN`, `GITHUB_PAT`, then
  anonymous access. An optional token environment variable can still be set explicitly for custom setups. GitHub log
  timestamps, groups, command markers, and ANSI color codes are compacted before display.
* **Usage**: Enjoy autocomplete, syntax highlighting, and much more as you code your GitHub Workflows and Actions.

## Local Development

The project uses the Gradle wrapper and Java 25. No manual JetBrains JDK path is needed; the IntelliJ Platform Gradle
Plugin downloads the IDE, bundled plugins, verifier, and test runtime.

1. Install Java 25 and make it available as `java`.
2. Run `./gradlew test` for the fast regression suite.
3. Run `./gradlew check verifyPlugin buildPlugin` before publishing or opening a release PR.

## Release Automation

One GitHub Actions workflow runs for branch pushes, PRs, and manual dispatches. It has one job and one cache. Branch and
PR runs do the normal test/package pass. A merge to `main`, or a manual workflow run, prepares the date-based version,
runs the full checks and Plugin Verifier, signs the ZIP, creates the GitHub release, publishes the signed ZIP to GitHub
Packages, and uploads the same signed ZIP to JetBrains Marketplace.

The workflow prunes old GitHub Actions caches after a successful non-PR run so only the current pipeline cache remains.

Required repository secrets:

* `CERTIFICATE_CHAIN`
* `PRIVATE_KEY`
* `PRIVATE_KEY_PASSWORD`
* `PUBLISH_TOKEN`

Optional repository secret:

* `RELEASE_TOKEN` - lets the workflow push the release commit and tag with a dedicated token. Without it, `GITHUB_TOKEN`
  is used.

Optional repository variable:

* `MARKETPLACE_CHANNEL` - empty means the default stable Marketplace channel.

For manual IDE testing, run `./gradlew runIde`. The default target tracks the latest stable IntelliJ IDEA platform that
the Gradle tooling can resolve (`platformVersion` in `gradle.properties`). The first run downloads IDE artifacts and can
take a while. The task also repairs stale custom color-scheme references in the generated sandbox only, so a missing
local theme is less likely to kick the test IDE back into light-mode betrayal. This is annoying, but at least it is
predictable. Progress.

## Dependencies

This plugin depends on:

* GitHub API integration
* YAML parser

## Unsupported Products

*There is no way to increase the product support until the dependencies are compatible with these products.*

* [JetBrains Client](https://www.jetbrains.com/help/idea/code-with-me-guest-ui-overview.html)
* [Code With Me Guest](https://www.jetbrains.com/help/idea/code-with-me.html)
* [JetBrains Gateway](https://www.jetbrains.com/de-de/remote-development/gateway/)

## Maintainer

Yuna Morgenstern, your GitHub Jedi.
[GitHub Profile](https://github.com/YunaBraska/github-workflow-plugin)

## Dive in and level up your GitHub game! 🌟

<!-- Plugin description end -->

#### Project Checklist

- [x] Autocomplete workflow and actions refs from resolved remote tags/branches e.g. `@main`, `@v1`, ...
- [x] Add links to Workflows and action files (GitHubUrl && MarketplaceUrl)
- [x] Add links to Definitions (jobs, steps, needs, secrets, inputs, envs)
  e.g. (https://github.com/cunla/ghactions-manager/blob/master/src/main/kotlin/com/dsoftware/ghmanager/api/Workflows.kt)
- [x] Generate GitHub context data from (https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#github-context)
- [x] Generate default environment variable data from (https://docs.github.com/en/actions/reference/workflows-and-actions/variables#default-environment-variables)

## Learning List

- [x] Create Tests
- [ ] Refactor remaining custom editor UI into smaller JetBrains-native pieces where it reduces gutter noise, disposal
  risk, or maintenance cost.
- [x] Auto Complete Uses with local action files
- [x] Auto Complete Uses with cached/current workflow refs
- [x] Auto Complete Uses field with Tags & Branches
- [x] Link local action/workflow files aka find usages
- [x] implement CMD+B
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [x] Get familiar with the [template documentation][template].
- [x] Adjust the [pluginGroup](./gradle.properties), [plugin ID](./src/main/resources/META-INF/plugin.xml).
- [x] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [x] Review
  the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [x] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate)
  for the first time.
- [x] Set the `21396-github-workflow` in the above README badges.
- [ ] Confirm the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate)
  related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Confirm
  the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Watch IntelliJ Platform Gradle Plugin and template releases during maintenance rounds.

Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
