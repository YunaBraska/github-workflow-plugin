# Editor Test Matrix

This matrix tracks editor behavior that should be tested through IntelliJ fixtures, not private helpers.

Official syntax references:

- GitHub workflow syntax: https://docs.github.com/actions/learn-github-actions/workflow-syntax-for-github-actions
- GitHub contexts: https://docs.github.com/actions/learn-github-actions/contexts
- GitHub action metadata: https://docs.github.com/en/actions/reference/workflows-and-actions/metadata-syntax

## Covered

- Action `with` input validation from resolved action metadata.
- Action `with` input completion from resolved remote action metadata, temp-file local `action.yaml` metadata, and project-local `action.yml` metadata.
- Local action `uses` reference resolution to nested `action.yml`, nested `action.yaml`, and root `action.yml`.
- Local `uses` completion for step-level action directories, root `action.yml`, and job-level reusable workflow files, with the wrong callable type excluded.
- Remote `uses` completion for callables and refs already known from the cache or current workflow.
- Remote `uses` target completion can discover matching owner repositories from configured GitHub servers before the
  callable metadata has been resolved.
- Remote `uses` ref completion can discover the latest 10 refs for a typed callable such as `actions/checkout@`.
- Remote `uses` ref completion for tags/branches resolved from public GitHub and GitHub Enterprise-shaped servers
  through fake HTTP servers.
- GitHub Enterprise servers registered in JetBrains GitHub settings are used as remote metadata sources; the plugin does
  not add a parallel server settings UI.
- Cache actions are registered through `plugin.xml`, localized through resource bundles, and covered by cache summary
  and clear behavior tests.
- The default resource bundle and 20 locale resource bundles keep matching key sets with nonblank values.
- Local reusable workflow `uses` reference resolution to project workflow files.
- Remote action and reusable workflow `uses` reference creation through IntelliJ `WebReference`, including exact GitHub URL target metadata.
- Configured GitHub Enterprise action references and styling keep the configured server URL instead of hard-coding github.com.
- Resolved local and remote `uses` values are styled as highlighted references.
- Resolved `uses` values expose quick documentation with action/workflow name, description, URL, inputs, outputs, and secrets.
- `needs` reference resolution to a previous job in scalar, quoted scalar, and array forms.
- Expression reference resolution for `inputs.*`, `inputs['name']`, `secrets.*`, `env.*`, `matrix.*`, `steps.*`, `steps.*.outputs.*`, `needs.*`, `needs.*.outputs.*`, reusable workflow `jobs.*`, and reusable workflow `jobs.*.outputs.*`.
- Expression quick documentation for resolved inputs, secrets, envs, matrix keys, steps, step outputs, needs, jobs, and
  context collection segments such as `outputs`.
- Multiple expressions in one scalar resolve only the expression segment at the caret.
- Resolved expression segments are styled as highlighted references where there is a local target.
- Resolved expression segments use a workflow variable color; job IDs, job names, step IDs, and step names use a workflow
  declaration color.
- Invalid `needs` job IDs in scalar/list form.
- `inputs.*` validation and completion for `workflow_call` and `workflow_dispatch` inputs.
- `secrets.*` validation for workflow call secrets.
- Automatic `secrets.GITHUB_TOKEN` highlighting and completion.
- Secret use inside `if` conditions.
- `env.*` validation and completion for workflow, job, step, Bash `$GITHUB_ENV`, PowerShell `$env:GITHUB_ENV`, and multiline file-command values.
- Default environment variable completion inside `run` shell text, with tests guarding current documented GitHub and
  runner variables.
- `github.*`, including current ID/protection keys from the official context reference, nested `github.event.*`,
  `runner.*`, including `runner.debug`, `job.*`, nested `job.container.*`, strict local `job.services.*`, `strategy.*`,
  `matrix.*`, and unknown external `vars.*` contexts.
- `gitea.*` context highlighting and completion uses the same key map as `github.*`.
- `job.services.<service_id>` validation/completion/reference/styling from local job service definitions, including `id`, `network`, `ports`, and mapped port keys.
- Matrix keys from direct `strategy.matrix` entries and `strategy.matrix.include`.
- `steps.<id>.outputs.<name>` validation and completion for previous run outputs, multiline `$GITHUB_OUTPUT`, `tee -a $GITHUB_OUTPUT`, and resolved action outputs.
- `steps.<id>.outputs.<name>` references resolve to the declaring run block for file-command outputs and to the
  producing `uses` step for action metadata outputs.
- Bracket notation validation for `inputs['name']`, `github['ref_name']`, `steps['id'].outputs['name']`, and `needs['job'].result`.
- Bracket notation completion for `inputs['']`, `github['']`, `steps['']`, `steps['id'].outputs['']`, `needs['']`, and `needs['job'].outputs['']`.
- `steps.<id>.outcome` and `steps.<id>.conclusion`.
- `needs.<job>.outputs.<name>` and `needs.<job>.result` validation and completion for direct job dependencies.
- `jobs.<job>.outputs.<name>` and `jobs.<job>.result` validation and completion for reusable workflow outputs.
- Remote and local reusable workflow `jobs.<job_id>.uses` input completion, input validation, secret completion, secret validation, `secrets: inherit`, and downstream output validation/completion.
- `with`/`secrets` parameters on resolved actions/workflows expose quick documentation with available metadata.
- Expression validation in documented context-bearing fields including `run-name`, `concurrency`, `jobs.<job_id>.concurrency.group`, `jobs.<job_id>.runs-on`, `jobs.<job_id>.environment.url`, `jobs.<job_id>.steps.continue-on-error`, and `jobs.<job_id>.steps.timeout-minutes`.
- Expression validation in additional documented context-bearing fields: `on.workflow_call.inputs.<input_id>.default`, scalar `jobs.<job_id>.container`, `jobs.<job_id>.container.credentials`, scalar `jobs.<job_id>.environment`, `jobs.<job_id>.strategy.fail-fast`, `jobs.<job_id>.strategy.max-parallel`, `jobs.<job_id>.defaults.run.shell`, and `jobs.<job_id>.services.<service_id>.credentials`.
- Completion/reference/styling synchronization for workflow-call input defaults.
- Expression validation in `runs-on` scalar and sequence values, folded block scalars, and multiline block-scalar expressions.
- Job output unused/used highlighting.
- Job outputs used inside expression functions such as `fromJson(needs.<job>.outputs.<output>)`.
- Folded scalar expression highlighting and alias-backed scalar/map environment keys.
- Composite action metadata input references and `runs.steps` output references/completion.
- Composite action `runs.steps` can reference outputs from previous `uses` steps.
- Root expression completion for locally available contexts.
- `$GITHUB_ENV` and `$GITHUB_OUTPUT` completion in `run` blocks.
- Quick-fix text for invalid action inputs, unknown workflow inputs, secrets in `if`, and unused job outputs.
- Quick-fix execution for input replacement, invalid action input deletion, invalid reusable workflow secret deletion, invalid suffix deletion, and invalid member deletion.
- Gutter/info action execution for action suppression, input suppression, and local action jump action dispatch.
- Gutter/info action execution for remote reload is tested with a controllable resolver boundary and no sleeps.
- Cache tools can refresh metadata, clear metadata, and restore suppressed action/input/output warnings.
- Regression tests cover prior issues for multiline outputs, `tee` outputs, composite `uses` step outputs,
  `needs.<job>.result` inside conditions, action metadata fallback to `action.yaml`, GitHub Enterprise metadata, failed
  remote downloads, and invalid virtual-file paths.
- A showcase workflow test covers a large mixed workflow with anchors, matrix strategy, services, local actions, local
  reusable workflows, remote actions, remote reusable workflows, Gitea context, job outputs, needs, functions, and file-command outputs.
- A bounded large-workflow highlighting performance test runs through the dedicated `performanceTest` Gradle task.

## Missing

- Full `vars.*` completion. Repository/organization/environment variables are external and need settings or GitHub API support.
- GitHub context/default-env data is generated into checked-in resource snapshots by `./gradlew generateGitHubDocsData`.
  Tests ensure completion/highlighting metadata uses exactly those snapshots.
