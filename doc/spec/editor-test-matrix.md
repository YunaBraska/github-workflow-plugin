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
- Resolved actions with cached major-version refs show an update quick-fix for older `v?\d+` refs.
- Remote `uses` ref completion for tags/branches resolved from public GitHub and GitHub Enterprise-shaped servers
  through fake HTTP servers.
- Gitea-compatible `/api/v1` remote metadata resolution is covered through fake HTTP tests and a default-on Docker smoke
  test against the official rootless Gitea image. The same Docker suite also registers `act_runner`, dispatches a real
  `.gitea/workflows` run through the plugin client, waits for completion, lists jobs, and downloads logs. Set
  `GITEA_DOCKER_TEST=false` to skip it.
- GitHub Enterprise servers registered in JetBrains GitHub settings are used as remote metadata sources; the plugin does
  not add a parallel server settings UI.
- Cache actions and settings are registered through `plugin.xml`, localized through resource bundles, and covered by
  cache summary, clear, inspect, selected-delete, import, and export behavior tests.
- The default resource bundle and 20 locale resource bundles keep matching key sets with nonblank values. Settings,
  inspection, workflow-run, and issue-report keys resolve for every configured locale.
- Locale bundles are checked for placeholder parity, encoding sanity, leaked generation tokens, and accidental English
  fallback except for short technical labels such as `API URL`, `Ref`, `OS`, and `PowerShell`.
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
- Completion stays workflow-aware outside `run:` blocks and deliberately avoids step/YAML key suggestions inside plain
  shell `run:` text; shell/global environment variable completion still works there.
- `github.*`, including current ID/protection keys from the official context reference, nested `github.event.*`,
  `runner.*`, including `runner.debug`, `job.*`, nested `job.container.*`, strict local `job.services.*`, `strategy.*`,
  `matrix.*`, and unknown external `vars.*` contexts.
- `gitea.*` context highlighting and completion uses the same key map as `github.*`.
- `.gitea/workflows/*` syntax uses Gitea token permission scopes, Gitea permission shorthand values, and Gitea cron
  alias completion without leaking GitHub-only permission scopes from the bundled GitHub schema.
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
- `shell:` completion for GitHub-supported shells.
- Typing newline, `:`, `.`, or `@` in workflow files can trigger relevant auto-popup completion through workflow-aware
  typed/enter handlers.
- Quick-fix text for invalid action inputs, unknown workflow inputs, secrets in `if`, and unused job outputs.
- Quick-fix execution for input replacement, invalid action input deletion, invalid reusable workflow secret deletion, invalid suffix deletion, and invalid member deletion.
- Quick-fix menu entries use icons while the editor gutter is kept reserved for durable reference/run markers instead of
  stacking every available intention in the line ruler.
- Gutter/info action execution for action suppression, input suppression, and local action jump action dispatch.
- Gutter/info action execution for remote reload is tested with a controllable resolver boundary and no sleeps.
- Left-ruler reference markers are intentionally limited to local action file targets and file-command declarations such
  as `$GITHUB_ENV` and `$GITHUB_OUTPUT`.
- Cache tools can refresh metadata, clear metadata, and restore suppressed action/input/output warnings.
- GitHub Workflow run configuration registration, workflow_dispatch input parsing, repository remote resolution,
  current-branch ref selection, dispatch/cancel/status/log client behavior, and gutter play registration for dispatchable
  workflows.
- `workflow_dispatch` does not show a run gutter action when no GitHub repository can be resolved.
- Workflow run HTTP behavior retries configured GitHub authorizations before anonymous access and reports 401/rate-limit
  failures with a GitHub settings hint.
- Workflow run HTTP behavior tries matching IDE accounts, then other IDE GitHub accounts, then configured/default
  environment tokens (`GITHUB_TOKEN`, `GH_TOKEN`, `GITHUB_PAT`), then anonymous access.
- Workflow run HTTP behavior reuses the first successful account authorization across polling calls and does not fall
  back to anonymous requests after an authenticated rate-limit response.
- Workflow run tools expose run/rerun-failed/rerun-all/cancel/delete/download-log/download-artifacts actions from the Run
  tool-window toolbar, only enabling downloads when GitHub exposes the relevant data.
- Workflow run process behavior streams job log deltas without auth-strategy noise, routes each GitHub job to the
  Run-window job tree/detail console, keeps a single workflow run tab, shows JUnit-style status icons and elapsed times,
  updates root/progress state for success/failure/cancel, summarizes temporary HTML/504 log failures as short "logs will
  appear" notices, quietly retries in-progress HTTP log failures, fetches completed job logs before the whole run
  completes, and the `workflow_dispatch` gutter marker switches to Stop while a run is tracked.
- Workflow run log rendering strips GitHub timestamps, strips ANSI controls while preserving warning/error/system
  meaning, formats GitHub `##[group]` / `##[endgroup]` / `##[/group]` markers as named blocks with four-digit line
  numbers, formats `##[command]` markers as `run:` lines, and classifies warning/error output for IDE console coloring.
- Regression tests cover prior issues for multiline outputs, `tee` outputs, composite `uses` step outputs,
  `needs.<job>.result` inside conditions, action metadata fallback to `action.yaml`, GitHub Enterprise metadata, failed
  remote downloads, and invalid virtual-file paths.
- A showcase workflow test covers a large mixed workflow with anchors, matrix strategy, services, local actions, local
  reusable workflows, remote actions, remote reusable workflows, Gitea context, job outputs, needs, functions, and file-command outputs.
- GitHub context/default-env data is generated into checked-in resource snapshots by `./gradlew generateGitHubDocsData`;
  tests ensure completion/highlighting metadata uses exactly those snapshots.
- A bounded large-workflow highlighting performance test runs through the dedicated `performanceTest` Gradle task.

## Missing

- Full `vars.*` completion. Repository/organization/environment variables are external and need settings or GitHub API support.
