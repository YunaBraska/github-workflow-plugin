<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitHub Workflow Plugin Changelog

## [Unreleased]

### Plugin Wiring

- Simplified workflow syntax routing for completion, highlighting, references, and documentation.
- Reduced duplicated workflow run, action cache, and schema handling internals while keeping behavior stable.
- Reorganized plugin internals into cleaner entry/client/git/run/settings/state/syntax boundaries, with public service
  Javadocs where humans might actually read them.
- Added the plugin size refactor plan and documented the release/changelog flow.
- Release automation now uses plain date tags, avoids duplicated Marketplace change-note headings, and groups Dependabot
  dependency updates into one weekly PR against `dev`.
- Updated the IntelliJ test platform, JaCoCo, and GitHub Actions cache action to current stable metadata.
- Fixed the README build badge link and refreshed navigation/release docs.
- `.gitea/workflows/*` files now get their own light/dark Gitea-flavored file icon instead of cosplaying GitHub.

## [2026.5.29] - 2026-05-29

### Release Polish

- Marketplace change notes skip the duplicated version heading.
- Future GitHub release tags use plain date versions without a leading `v`.
- Dependabot dependency bumps are grouped into one weekly cross-ecosystem PR against `dev`.

## [2026.5.23] - 2026-05-23

### Fresh Start

- Better workflow editing with broader completion, highlighting, navigation, and hover help for GitHub Actions files.
- Resolved action and reusable workflow metadata now improves `with`, `secrets`, `outputs`, and version suggestions.
- Major-version action refs can now show an update quick fix when newer cached refs are available.
- GitHub Workflow Run Configurations can dispatch `workflow_dispatch`, collect inputs, show run status/logs, and cancel
  remote runs from the IDE Run tool window.
- Gutter-created workflow runs now default to the current checked-out branch instead of always using `main`.
- Workflow runs now try IDE accounts in host-priority order, then configured/default local env tokens
  (`GITHUB_TOKEN`, `GH_TOKEN`, `GITHUB_PAT`), then anonymous access, so live logs can fall back to a stronger token when
  an IDE account token can dispatch but cannot download in-progress logs.
- `shell:` now completes GitHub-supported shell values.
- Public GitHub and GitHub Enterprise accounts configured in the IDE are used for action metadata resolution.
- Workflow expressions inside strings are highlighted separately from surrounding text.
- Shell snippets inside `run` blocks can use shell-aware editing where the IDE supports it.
- Cache controls are available through Find Action and, where visible, `Tools > GitHub Workflow` to refresh metadata,
  clear cached entries, or restore hidden warnings.
- The plugin build, tests, verifier checks, release packaging, PR build checks, and local development setup are ready for the next version.
- Release automation now supports manual workflow runs, PR/branch testing, merged-PR tagging, tag-based GitHub releases,
  and release-based JetBrains Marketplace publishing.
- Release tags now use date-based SemVer (`vYYYY.M.D`); the tag workflow updates `pluginVersion`.
- Thanks to @SilverNicktail, @tomsit-ionos, @jbw9964, @nyurik, @Lordfirespeed, @ris58h, @holomekc,
  @InSyncWithFoo, @LecrisUT, @enobrev, @bartei, @gilzow, @zaaraungkam, @PeerHofmannGSG, and @zwj-cheer for reports and
  context that shaped this hardening round.

### Workflow Runs

- Workflow runs now use one Run tool-window view with a JUnit-style workflow tree, grouped jobs, selected-node log
  output, test-style status icons, and a thin progress bar instead of separate job tabs.
- Workflow job logs now render GitHub `group` blocks as named sections, reset to tidy `0001 |` line numbers per block,
  show command markers as `run:`, strip ANSI escape noise, and classify warnings/errors for colored console output.

### Settings And Localization

- Added a `Settings > Tools > GitHub Workflow` page for language override, cache review/delete, cache import/export,
  cache memory estimates, and a small support button with nerd fuel.
- Moved common run-configuration, cache, quick-fix, documentation, and settings strings into resource bundles and added
  locale coverage checks for the top-20 language bundle set.

## [3.2.1] - 2023-11-04

### Investigating UI Freeze

- Fix: [UI freeze when opening a repo with large amount of JS files](https://github.com/YunaBraska/github-workflow-plugin/issues/39)

## [3.2.0] - 2023-10-14

### Acknowledge undefined action inputs & outputs

- Feat: [#33 Acknowledge undefined action inputs & outputs](https://github.com/YunaBraska/github-workflow-plugin/issues/33)
- Feat: Click on `Needs` navigates directly to the `Job`
- Fix: [Startup error: Slow operations are prohibited on EDT](https://github.com/YunaBraska/github-workflow-plugin/issues/38)
- Fix: [InvalidPathException](https://github.com/YunaBraska/github-workflow-plugin/issues/40)
- Fix: Keep user settings after action or cache refresh
- Chore: refactored & split logic to specific classes
  like \[`Action`, `Envs`, `GitHub`, `Inputs`, `Jobs`, `Needs`, `Runner`, `Secrets`, `Steps`]

## [3.1.0] - 2023-10-14

### Code Completion for steps outcome && conclusion

- Feat: Code Completion for steps outcome &&
  conclusion [#37 Schema Cache](https://github.com/YunaBraska/github-workflow-plugin/pull/37) Big thanks
  to [Siketyan](https://github.com/siketyan)

## [3.0.4] - 2023-10-10

### Fix

- Fix: [#34 Schema Cache](https://github.com/YunaBraska/github-workflow-plugin/issues/34) - embarrassing freezing UI
  while downloading schema files. The Schema files are now preloaded.

## [3.0.3] - 2023-10-09

### Fix

- Fix: [#32 Schema Cache](https://github.com/YunaBraska/github-workflow-plugin/issues/32) - was still using the file
  system instead of the new IDE cache.

## [3.0.2] - 2023-10-08

### Fix

- Feat: Added autocompletion for `$GITHUB_OUTPUT` and `$GITHUB_ENV`
-

Fix: [#31 StringIndexOutOfBoundsException (GitHubAction.java:87)](https://github.com/YunaBraska/github-workflow-plugin/issues/31)

- Fix: Secrets are unsupported on if statements
- Fix: highlighting issues when workflow is very long
- Fix: Code Completion shows only available items [inputs, secrets, jobs, steps, needs]
- Fix: Exchanged too complex Regex variable matcher with a dedicated function

## [3.0.1] - 2023-10-01

### Fix

- Fix: [#29 Unresolved workflow outputs](https://github.com/YunaBraska/github-workflow-plugin/issues/29)

## [3.0.0] - 2023-09-25

### Refactoring

- Feature: [#20 Code Completion for action.yml](https://github.com/YunaBraska/github-workflow-plugin/issues/20)
- Feature: [#22 Disable checks](https://github.com/YunaBraska/github-workflow-plugin/issues/22)
- Fix: [#24 Cannot resolve step-id in job outputs](https://github.com/YunaBraska/github-workflow-plugin/issues/24)
- Fix: [#25 Cache Steps with no uses arend recognised](https://github.com/YunaBraska/github-workflow-plugin/issues/25)
- Fix: [#21 Cache NullPointerException](https://github.com/YunaBraska/github-workflow-plugin/issues/21)
- Fix: [#26 Cache NullPointerException](https://github.com/YunaBraska/github-workflow-plugin/issues/26)
- Chore: [#18 Cleanup Memory Leaks](https://github.com/YunaBraska/github-workflow-plugin/issues/18)
- Chore: [#17 Add Tests](https://github.com/YunaBraska/github-workflow-plugin/issues/17)
- Chore: Exchanged custom Objects with JetBrains Objects
- Chore: Moved IO cache to Application Scoped cache

## [2.2.7] - 2023-08-26

### Maintenance

- Local action support

## [2.2.6] - 2023-08-21

### Added QuickFix Icons

- Added Annotation QuickFix Icons
- Fix: #12 Progressbar bug which broke the background resolver from GitHub actions and workflows

## [2.2.5] - 2023-08-18

### Intellisense for Windows

- Added windows support ... don't ask ^^

## [2.2.4] - 2023-08-17

### Responsive Highlights

- Syntax Highlighting refreshes now on typing
- Exchanged `Clear Cache` with `Reload`

## [2.2.3] - 2023-08-15

### Continue Renovations

- Stabilised code completion on `with` fields
- Added support for `composite-actions`

## [2.2.2] - 2023-08-13

### Continue Renovations

- Added GitHub icons to detected files
- Stabilised Listeners/Disposable
- Resolve `uses` on Code Completion in case background process is not finished
- Fix for multi Projects - acting now on the right project from context

## [2.2.1] - 2023-08-13

### Continue Renovations

- Added Dependabot Schema Validation
- Added automated Error Reporting
- Cleanups

## [2.2.0] - 2023-08-13

### Continue Renovations

- Added Dependabot Schema Validation

## [2.1.0] - 2023-08-13

### Continue Renovations

- Added automated Error Reporting

## [2.0.4] - 2023-08-10

### Continue Renovations

- Fixed EDT Error
- Fixed Disposable Error
- Added clear Workflow cache option
- Fixed false positives at Syntax Highlighting

## [2.0.3] - 2023-08-10

### Continue Renovations

- Fixed EDT Error
- Fixed false positives at Syntax Highlighting

## [2.0.0] - 2023-07-05

### Big Refactoring

- Added links to `uses` fields - press `ALT + RETURN`
- Added completion for `Runner`
- Added support for `private` GitHub repositories using GitHub Account
- Started visual validations with quick fixes
- Exchanged YAML Parser to `org.jetbrains.yaml`
- Fixed few bugs

## [1.0.1] - 2023-04-09

### Happy Easter

- Stabilised Autocompletion
- Added new Dark and Light Icon
- Big refactoring for first Major Version

## [0.1.0] - 2023-04-06

### Happy Easter

- Assign schemas automatically (Workflow, Action, funding, issue template, issue config, discussion)
- Added completion in `if` statements for [`steps`, `jobs`, `outputs`, `env`, `github`, `var`]
- Added completion  [`needs`]
- Added support for step outputs using  [`use: action/or/workflow`]
- Added support for step outputs using  [`GITHUB_OUTPUT`]
- Added `Icons` and added `priorities` to node completion values
- Added File cache for [`workflow`, `action`, `schema`]
- Exchanged java native with Intellij downloader for downloading content.

## [0.0.4] - 2023-04-03

### Initial Plugin

- Raw concept
