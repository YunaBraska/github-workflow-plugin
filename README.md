# GitHub Workflow Plugin

*Your Ultimate Wingman for GitHub Workflows and Actions! 🚀*

![Build](https://github.com/YunaBraska/github-workflow-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/YunaBraska)
[![](https://img.shields.io/static/v1?label=DataPrivacy&message=%F0%9F%94%92&logo=springsecurity&color=%#6DB33F)](docs/DataPrivacy.md)

## Why Choose GitHub Workflow Plugin?

<!-- Plugin description -->
Say goodbye to tedious trial and error! Experience seamless GitHub workflow management like never before. Create
pipelines at the speed of thought with this JetBrains plugin that extends support for GitHub Actions and Workflows.
_[See Screenshots](https://plugins.jetbrains.com/plugin/21396-github-workflow)_

## Key Features

* 🌈 Autocomplete & Syntax Highlighting: Write workflow YAML files with confidence. Autocomplete suggestions and clear
  syntax highlighting will make your code look and feel pristine.
* 🚀 Repository Access: Integrate with your private GitHub repositories for those secret projects you're working on.
* 🗺️ Local Path Resolution: Navigate effortlessly with one-click access to local paths.
* ✅ Validation Engine: Validates linked local actions and workflows, but hey, you can turn this off too.
* 🛡️ Security: We respect your privacy! The plugin doesn't use or store your personal data; it only accesses remote
  actions and workflows when necessary.
* 🧩 Extensive Schema Support: Covers Depentabot, Actions, Workflows, Founding, Issue Config, Issue Forms, and Workflow
  Templates for comprehensive project management.

## Getting Started

* **Installation**: Download the plugin
  from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/21396-github-workflow).
* **Configuration**: Add your GitHub account via `File > Settings > Version Control > GitHub`.
* **Usage**: Enjoy autocomplete, syntax highlighting, and much more as you code your GitHub Workflows and Actions.

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

- [ ] Autocomplete workflow and actions refs e.g. `@main`, `@v1`, ...
- [ ] Add links to Workflows and action files (GitHubUrl && MarketplaceUrl)
- [ ] Add links to Definitions (jobs, steps, needs, secrets, inputs, envs)
  e.g. (https://github.com/cunla/ghactions-manager/blob/master/src/main/kotlin/com/dsoftware/ghmanager/api/Workflows.kt)
- [ ] Autogenerate `getGitHubContextEnvs`
  from (https://docs.github.com/en/actions/learn-github-actions/contexts#github-context)
- [ ] Autogenerate `getGitHubEnvs`
  from (https://docs.github.com/en/actions/learn-github-actions/variables#using-the-vars-context-to-access-configuration-variable-values)

## Learning List

- [ ] Create Tests
- [ ] Refactor - less custom elements == less memory leaks
- [ ] Auto Complete Uses with local action files
- [ ] Auto Complete Uses field with Tags & Branches
- [ ] Link local files action files aka find usages
- [ ] implement CMD+B
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
