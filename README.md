# GitHub Workflow Plugin

*Your Ultimate Wingman for GitHub Workflows and Actions! 🚀*

![Build](https://github.com/YunaBraska/github-workflow-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/YunaBraska)
[![](https://img.shields.io/static/v1?label=DataPrivacy&message=%F0%9F%94%92&logo=springsecurity&color=%#6DB33F)](docs/DataPrivacy.md)

## ⚠️ Development Pause Notice ⚠️

As of 01.01.2024, active development on this plugin is **paused**. Recent updates from JetBrains have introduced several
disruptions that significantly impact the plugin. Despite my previous year's intensive efforts.

🔥 **I am actively seeking contributors** who can help tackle these challenges. If you're interested in contributing, or
know someone who might be, please feel free to dive in. I am more than willing to guide new contributors through the
plugin's architecture and collaborate on overcoming the current obstacles.

Thank you for your understanding and support!

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
* 🧹 Cache Controls: Refresh or clear resolved action/workflow metadata from `Tools > GitHub Workflow`.
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
* **Cache**: Use `Tools > GitHub Workflow` to refresh stale remote metadata or clear cached action/workflow metadata.
* **Usage**: Enjoy autocomplete, syntax highlighting, and much more as you code your GitHub Workflows and Actions.

## Local Development

The project uses the Gradle wrapper and Java 25. No manual JetBrains JDK path is needed; the IntelliJ Platform Gradle
Plugin downloads the IDE, bundled plugins, verifier, and test runtime.

1. Install Java 25 and make it available as `java`.
2. Run `./gradlew test` for the fast regression suite.
3. Run `./gradlew check verifyPlugin buildPlugin` before publishing or opening a release PR.

For manual IDE testing, run `./gradlew runIde`. The first run downloads IDE artifacts and can take a while. This is
annoying, but at least it is predictable. Progress.

Current UX/DX gaps are tracked in [UX/DX Gaps](doc/spec/ux-dx-gaps.md); editor behavior coverage is tracked in
[Editor Test Matrix](doc/spec/editor-test-matrix.md).

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

#### TODO

- [x] Autocomplete workflow and actions refs from resolved remote tags/branches e.g. `@main`, `@v1`, ...
- [x] Add links to Workflows and action files (GitHubUrl && MarketplaceUrl)
- [x] Add links to Definitions (jobs, steps, needs, secrets, inputs, envs)
  e.g. (https://github.com/cunla/ghactions-manager/blob/master/src/main/kotlin/com/dsoftware/ghmanager/api/Workflows.kt)
- [x] Generate GitHub context data from (https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#github-context)
- [x] Generate default environment variable data from (https://docs.github.com/en/actions/reference/workflows-and-actions/variables#default-environment-variables)

## Learning List

- [x] Create Tests
- [ ] Refactor - less custom elements == less memory leaks
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
- [ ] Set the `21396-github-workflow` in the above README badges.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate)
  related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set
  the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified
  about releases containing new features and fixes.

Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
