# GitHub Workflow Plugin

![Build](https://github.com/YunaBraska/github-workflow-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/21396-github-workflow.svg)](https://plugins.jetbrains.com/plugin/21396-github-workflow)

<!-- Plugin description -->
Autocompletion for GitHub workflows
Enjoy lightning-fast autocompletion for

* actions
* workflows
* inputs
* envs
* secrets
* jobs

**Phase: Beta**

<!-- Plugin description end -->

#### TODO

- [ ] Automatically change File Schema to `GitHub Workflow`
- [ ] Autocomplete needs: e.g. `needs: my_job_id`
- [ ] Add `.` to autocompletion of `steps`, `jobs`, `outputs`, `env`, `github`, `var`
- [ ] Add autocompletion in `if` statements (`getCaretBracketItem`)
- [ ] Autocomplete env(s) from echo outputs e.g. `run: echo "DYNAMIC_VARIABLE=some value" >> $GITHUB_ENV`
- [ ] Autocomplete outputs(s) from step echo outputs e.g. `echo "has_changes=$has_changes" >> $GITHUB_OUTPUT`
- [ ] Autocomplete workflow and actions refs e.g. `@main`, `@v1`, ...
- [ ] Autocomplete env(s) from current step input e.g. `env: CC_TEST_REPORTER_ID: ${{ secrets.CC_TEST_REPORTER_ID }}`
- [ ] Add links to Workflows and action files (GitHubUrl && MarketplaceUrl)
- [ ] Download files via `GithubApiRequest` && set custom `user agent`
  e.g. (https://github.com/cunla/ghactions-manager/blob/master/src/main/kotlin/com/dsoftware/ghmanager/api/Workflows.kt)
- [ ] Autogenerate `getGitHubContextEnvs`
  from (https://docs.github.com/en/actions/learn-github-actions/contexts#github-context)
- [ ] Autogenerate `getGitHubEnvs`
  from (https://docs.github.com/en/actions/learn-github-actions/variables#using-the-vars-context-to-access-configuration-variable-values)
- [ ] Add syntax highlighting

Fixes:

- [ ] Autocomplete env(s) only for the current jobs not for a previous job
- [ ] Autocomplete steps only for the current jobs not for a previous job
- [ ] Autocomplete secrets only for the current jobs not for a previous job

## Learning List

- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [x] Get familiar with the [template documentation][template].
- [x] Adjust the [pluginGroup](./gradle.properties), [plugin ID](./src/main/resources/META-INF/plugin.xml)
  and [sources package](./src/main/kotlin).
- [x] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [x] Review
  the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate)
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
