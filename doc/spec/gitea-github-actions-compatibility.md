# Gitea And GitHub Actions Compatibility

Last checked: 2026-06-11.

This note tracks behavior where Gitea Actions is close to GitHub Actions, but not identical. The plugin should prefer
shared behavior first and branch only where Gitea really differs. Tiny forks, not a hydra.

## Sources

- Gitea Actions comparison: https://docs.gitea.com/usage/actions/comparison
- Gitea Actions job token permissions: https://docs.gitea.com/usage/actions/token-permissions
- Gitea Actions variables and `gitea.*` context: https://docs.gitea.com/usage/actions/actions-variables
- Gitea API reference and OpenAPI spec: https://docs.gitea.com/api/
- Gitea OpenAPI source used for endpoint checks: https://docs.gitea.com/redocusaurus/plugin-redoc-1.yaml
- GitHub workflow REST API: https://docs.github.com/en/rest/actions/workflows?apiVersion=2026-03-10
- GitHub workflow run REST API: https://docs.github.com/en/rest/actions/workflow-runs?apiVersion=2026-03-10
- GitHub workflow job REST API: https://docs.github.com/en/rest/actions/workflow-jobs?apiVersion=2026-03-10
- GitHub artifact REST API: https://docs.github.com/en/rest/actions/artifacts?apiVersion=2026-03-10

## Already Handled

| Area | GitHub | Gitea | Plugin behavior |
| --- | --- | --- | --- |
| Workflow home | `.github/workflows/*` | `.gitea/workflows/*` | Both workflow roots are detected. |
| API base | `https://api.github.com` or enterprise `/api/v3` | Instance `/api/v1` | Run and metadata code infer provider behavior from API URL and workflow path. |
| API auth header | `Authorization: Bearer ...` | `Authorization: token ...` | Gitea providers and runs use `token ...`; GitHub keeps IDE account plus bearer token priority. |
| Token env fallback | `GITHUB_TOKEN`, `GH_TOKEN`, `GITHUB_PAT` | `GITEA_TOKEN`, `GITEA_PAT` | Gitea runs and metadata use Gitea token env names before anonymous access. |
| Workflow dispatch result | GitHub returns run details on current API version | Gitea returns run details when `return_run_details=true` | Gitea dispatch adds `return_run_details=true`; both parse `workflow_run_id`, `run_url`, and `html_url`. |
| Run discovery | Workflow-scoped or repository-scoped run listing | Repository-scoped `actions/runs` with `limit` and run `path` | GitHub keeps workflow-scoped discovery; Gitea reads a small repo-level window and picks the same workflow closest to dispatch time. |
| Job logs | `/actions/jobs/{job_id}/logs` | Same path in Gitea OpenAPI | Existing job log download path is shared. |
| Run jobs | `/actions/runs/{run}/jobs` | Same path in Gitea OpenAPI | Existing job tree polling path is shared. |
| Artifacts | Run artifact list plus artifact ZIP | Same core paths in Gitea OpenAPI | Existing artifact list/download path is shared. |
| Context root | `github.*` | `gitea.*`, with `github.*` as alias | Completion/highlighting covers `gitea.*` using the GitHub-compatible key map. |
| Absolute action URLs | GitHub syntax is usually `owner/repo[/path]@ref` | Absolute URLs are supported | Remote resolver supports absolute URLs for configured servers. |
| Cron aliases | GitHub uses POSIX cron expressions | Gitea also accepts `@yearly`, `@monthly`, `@weekly`, `@daily`, `@hourly` | `.gitea/workflows` cron completion suggests the Gitea aliases. |
| Permission scopes | GitHub has GitHub-only scopes such as `id-token`, `statuses`, `pages` | Gitea has `code`, `releases`, `wiki`, `projects`, and a smaller shared scope set | `.gitea/workflows` completion and validation use the Gitea scope set. |
| Permission shorthand | GitHub completion includes `read-all`, `write-all`, and `{}` | Gitea documents `read-all` and `write-all` scalar values | `.gitea/workflows` shorthand completion stays on documented Gitea values. |
| Complex `runs-on` | GitHub accepts richer runner targeting | Gitea supports scalar or single-item array forms | `.gitea/workflows` warns on multi-label arrays without affecting GitHub files. |
| Expression functions | GitHub has a wider function set | Gitea documents `always()` only | `.gitea/workflows` completion offers `always()` and warns on known GitHub-only functions. |
| Ignored job keys | GitHub executes `timeout-minutes`, `continue-on-error`, and `environment` | Gitea accepts but ignores them | `.gitea/workflows` keeps the keys valid and labels them as accepted runtime no-ops. |
| Runtime names | GitHub exposes `GITHUB_TOKEN` and `GITHUB_*` names | Gitea exposes `GITEA_TOKEN` and `GITEA_*` names too | `.gitea/workflows` completion prefers `secrets.GITEA_TOKEN` and includes Gitea runner env variables. |

## Known Limitations / Do Not Guess

| Area | Gitea behavior | Plugin risk | Suggested handling |
| --- | --- | --- | --- |
| Problem matchers and annotations | Gitea ignores problem matchers and workflow command annotations. | Log rendering can still color warnings/errors locally; remote UI may not. | No code change needed unless we add Gitea-specific docs. |
| Default action source | Gitea may resolve unqualified `uses:` through instance config (`github` or `self`). | Plugin cannot know server admin config. | Keep configured-server absolute URL support; do not guess admin config. |
| Secret and variable names | Gitea disallows user-created names starting with `GITHUB_` or `GITEA_`; variables are uppercased. | Future settings/UI for external variables must enforce Gitea naming rules. | Built-in names are completed; external variable CRUD does not exist yet. |

## Test Shape

- Fake HTTP tests cover provider inference, auth header scheme, dispatch URL, and run discovery URL.
- The Docker-backed Gitea integration test runs by default and seeds a tiny repository to verify `/api/v1` metadata
  resolution for actions and `.gitea/workflows`.
- Keep Docker test opt-out explicit with `GITEA_DOCKER_TEST=false` for machines where Docker is unavailable.
