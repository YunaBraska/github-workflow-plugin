<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# GitHub Workflow Plugin Changelog

## [Unreleased]

- composite-actions

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
