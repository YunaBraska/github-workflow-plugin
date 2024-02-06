package com.github.yunabraska.githubworkflow.helper;


import com.github.yunabraska.githubworkflow.Annotation;
import com.github.yunabraska.githubworkflow.WorkflowFile;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SuppressWarnings("java:S2386")
public class GitHubWorkflowConfig {

    public static final Pattern PATTERN_GITHUB_OUTPUT = Pattern.compile("echo\\s+\"(\\w+)=(.*?)\"\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_OUTPUT\\}?\"?");
    public static final Pattern PATTERN_GITHUB_ENV = Pattern.compile("echo\\s+\"(\\w+)=(.*?)\"\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_ENV\\}?\"?");
    public static final Key<SmartPsiElementPointer<PsiElement>> GHW_ELEMENT_REFERENCE_KEY = new Key<>("GHW_ELEMENT_REFERENCE_KEY");
    public static final Key<Annotation> GHW_ANNOTATION_KEY = new Key<>("GHW_REPLACE_WITH_KEY");
    public static final Key<WorkflowFile> GHW_WORKFLOW_KEY = new Key<>("GHW_WORKFLOW_KEY");
    public static final long CACHE_ONE_DAY = 24L * 60 * 60 * 1000;
    public static final String FIELD_ON = "on";
    public static final String FIELD_IF = "if";
    public static final String FIELD_ID = "id";
    public static final String FIELD_ENVS = "env";
    public static final String FIELD_RUN = "run";
    public static final String FIELD_RUNS = "runs";
    public static final String FIELD_JOBS = "jobs";
    public static final String FIELD_VARS = "vars";
    public static final String FIELD_WITH = "with";
    public static final String FIELD_USES = "uses";
    public static final String FIELD_NEEDS = "needs";
    public static final String FIELD_STEPS = "steps";
    public static final String FIELD_RUNNER = "runner";
    public static final String FIELD_GITHUB = "github";
    public static final String FIELD_DEFAULT = "${{}}";
    public static final String FIELD_INPUTS = "inputs";
    public static final String FIELD_OUTPUTS = "outputs";
    public static final String FIELD_SECRETS = "secrets";
    public static final String FIELD_CONCLUSION = "conclusion";
    public static final String FIELD_OUTCOME = "outcome";
    public static final Map<String, Supplier<Map<String, String>>> DEFAULT_VALUE_MAP = initProcessorMap();

    private static Map<String, Supplier<Map<String, String>>> initProcessorMap() {
        final Map<String, Supplier<Map<String, String>>> result = new HashMap<>();
        result.put(FIELD_GITHUB, GitHubWorkflowConfig::getGitHubContextEnvs);
        result.put(FIELD_ENVS, GitHubWorkflowConfig::getGitHubEnvs);
        result.put(FIELD_RUNNER, GitHubWorkflowConfig::getRunnerItems);
        result.put(FIELD_DEFAULT, GitHubWorkflowConfig::getCaretBracketItems);
        return result;
    }

    private static HashMap<String, String> getRunnerItems() {
        final HashMap<String, String> result = new HashMap<>();
        result.put("name", "The name of the runner executing the job.");
        result.put("os", "The operating system of the runner executing the job. Possible values are Linux, Windows, or macOS.");
        result.put("arch", "The architecture of the runner executing the job. Possible values are X86, X64, ARM, or ARM64.");
        result.put("temp", "The path to a temporary directory on the runner. This directory is emptied at the beginning and end of each job. Note that files will not be removed if the runner's user account does not have permission to delete them.");
        result.put("tool_cache", "he path to the directory containing preinstalled tools for GitHub-hosted runners. For more information, see \"About GitHub-hosted runners\".");
        result.put("debug", "The path to the directory containing preinstalled tools for GitHub-hosted runners. For more information, see \"About GitHub-hosted runners\".");
        return result;
    }

    private static HashMap<String, String> getCaretBracketItems() {
        final HashMap<String, String> result = new HashMap<>();
        result.put(FIELD_INPUTS, "Workflow inputs e.g. from workflow_dispatch, workflow_call");
        result.put(FIELD_SECRETS, "Workflow secrets");
        result.put(FIELD_JOBS, "Workflow jobs");
        result.put(FIELD_STEPS, "steps with 'id' of the current job");
        result.put(FIELD_ENVS, "Environment variables from jobs amd steps");
        result.put(FIELD_VARS, "The vars context contains custom configuration variables set at the organization, repository, and environment levels. For more information about defining configuration variables for use in multiple workflows");
        result.put(FIELD_NEEDS, "Identifies any jobs that must complete successfully before this job will run. It can be a string or array of strings. If a job fails, all jobs that need it are skipped unless the jobs use a conditional statement that causes the job to continue.");
        result.put(FIELD_GITHUB, "Information about the workflow run and the event that triggered the run. You can also read most of the github context data in environment variables. For more information about environment variables");
        return result;
    }

    //TODO: autogenerate this
    //https://docs.github.com/en/actions/learn-github-actions/contexts#github-context
    private static HashMap<String, String> getGitHubContextEnvs() {
        final HashMap<String, String> result = new HashMap<>();
        result.put("action", "The name of the action currently running, or the id of a step. GitHub removes special characters, and uses the name __run when the current step runs a script without an id. If you use the same action more than once in the same job, the name will include a suffix with the sequence number with underscore before it. For example, the first script you run will have the name __run, and the second script will be named __run_2. Similarly, the second invocation of actions/checkout will be actionscheckout2.");
        result.put("action_path", "The path where an action is located. This property is only supported in composite actions. You can use this path to access files located in the same repository as the action, for example by changing directories to the path:  cd ${{ github.action_path }} .");
        result.put("action_ref", "For a step executing an action, this is the ref of the action being executed. For example, v2.");
        result.put("action_repository", "For a step executing an action, this is the owner and repository name of the action. For example, actions/checkout.");
        result.put("action_status", "For a composite action, the current result of the composite action.");
        result.put("actor", "The username of the user that triggered the initial workflow run. If the workflow run is a re-run, this value may differ from github.triggering_actor. Any workflow re-runs will use the privileges of github.actor, even if the actor initiating the re-run (github.triggering_actor) has different privileges.");
        result.put("actor_id", "The account ID of the person or app that triggered the initial workflow run. For example, 1234567. Note that this is different from the actor username.");
        result.put("api_url", "The URL of the GitHub REST API.");
        result.put("base_ref", "The base_ref or target branch of the pull request in a workflow run. This property is only available when the event that triggers a workflow run is either pull_request or pull_request_target.");
        result.put("env", "Path on the runner to the file that sets environment variables from workflow commands. This file is unique to the current step and is a different file for each step in a job. For more information, see \"Workflow commands for GitHub Actions.\"");
        result.put("event", "The full event webhook payload. You can access individual properties of the event using this context. This object is identical to the webhook payload of the event that triggered the workflow run, and is different for each event. The webhooks for each GitHub Actions event is linked in \"Events that trigger workflows.\" For example, for a workflow run triggered by the push event, this object contains the contents of the push webhook payload.");
        result.put("event_name", "The name of the event that triggered the workflow run.");
        result.put("event_path", "The path to the file on the runner that contains the full event webhook payload.");
        result.put("graphql_url", "The URL of the GitHub GraphQL API.");
        result.put("head_ref", "The head_ref or source branch of the pull request in a workflow run. This property is only available when the event that triggers a workflow run is either pull_request or pull_request_target.");
        result.put("job", "The job_id of the current job. \nNote: This context property is set by the Actions runner, and is only available within the execution steps of a job. Otherwise, the value of this property will be null.");
        result.put("job_workflow_sha", "For jobs using a reusable workflow, the commit SHA for the reusable workflow file.");
        result.put("path", "Path on the runner to the file that sets system PATH variables from workflow commands. This file is unique to the current step and is a different file for each step in a job. For more information, see \"Workflow commands for GitHub Actions.\"");
        result.put("ref", "The fully-formed ref of the branch or tag that triggered the workflow run. For workflows triggered by push, this is the branch or tag ref that was pushed. For workflows triggered by pull_request, this is the pull request merge branch. For workflows triggered by release, this is the release tag created. For other triggers, this is the branch or tag ref that triggered the workflow run. This is only set if a branch or tag is available for the event type. The ref given is fully-formed, meaning that for branches the format is refs/heads/<branch_name>, for pull requests it is refs/pull/<pr_number>/merge, and for tags it is refs/tags/<tag_name>. For example, refs/heads/feature-branch-1.");
        result.put("ref_name", "The short ref name of the branch or tag that triggered the workflow run. This value matches the branch or tag name shown on GitHub. For example, feature-branch-1.");
        result.put("ref_protected", "true if branch protections are configured for the ref that triggered the workflow run.");
        result.put("ref_type", "The type of ref that triggered the workflow run. Valid values are branch or tag.");
        result.put("repository", "The owner and repository name. For example, octocat/Hello-World.");
        result.put("repository_id", "The ID of the repository. For example, 123456789. Note that this is different from the repository name.");
        result.put("repository_owner", "The repository owner's username. For example, octocat.");
        result.put("repository_owner_id", "The repository owner's account ID. For example, 1234567. Note that this is different from the owner's name.");
        result.put("repositoryUrl", "The Git URL to the repository. For example, git://github.com/octocat/hello-world.git.");
        result.put("retention_days", "The number of days that workflow run logs and artifacts are kept.");
        result.put("run_id", "A unique number for each workflow run within a repository. This number does not change if you re-run the workflow run.");
        result.put("run_number", "A unique number for each run of a particular workflow in a repository. This number begins at 1 for the workflow's first run, and increments with each new run. This number does not change if you re-run the workflow run.");
        result.put("run_attempt", "A unique number for each attempt of a particular workflow run in a repository. This number begins at 1 for the workflow run's first attempt, and increments with each re-run.");
        result.put("secret_source", "The source of a secret used in a workflow. Possible values are None, Actions, Dependabot, or Codespaces.");
        result.put("server_url", "The URL of the GitHub server. For example: https://github.com.");
        result.put("sha", "The commit SHA that triggered the workflow. The value of this commit SHA depends on the event that triggered the workflow. For more information, see \"Events that trigger workflows.\" For example, ffac537e6cbbf934b08745a378932722df287a53.");
        result.put("token", "A token to authenticate on behalf of the GitHub App installed on your repository. This is functionally equivalent to the GITHUB_TOKEN secret. For more information, see \"Automatic token authentication.\" \nNote: This context property is set by the Actions runner, and is only available within the execution steps of a job. Otherwise, the value of this property will be null.");
        result.put("triggering_actor", "The username of the user that initiated the workflow run. If the workflow run is a re-run, this value may differ from github.actor. Any workflow re-runs will use the privileges of github.actor, even if the actor initiating the re-run (github.triggering_actor) has different privileges.");
        result.put("workflow", "The name of the workflow. If the workflow file doesn't specify a name, the value of this property is the full path of the workflow file in the repository.");
        result.put("workflow_ref", "The ref path to the workflow. For example, octocat/hello-world/.github/workflows/my-workflow.yml@refs/heads/my_branch.");
        result.put("workflow_sha", "The commit SHA for the workflow file.");
        result.put("workspace", "The default working directory on the runner for steps, and the default location of your repository when using the checkout action.");
        return result;
    }

    //TODO: autogenerate this
    //https://docs.github.com/en/actions/learn-github-actions/variables#using-the-vars-context-to-access-configuration-variable-values
    private static HashMap<String, String> getGitHubEnvs() {
        final HashMap<String, String> result = new HashMap<>();
        result.put("CI", "Always set to true.");
        result.put("GITHUB_ACTION", "The name of the action currently running, or the id of a step. For example, for an action, __repo-owner_name-of-action-repo.\n\nGitHub removes special characters, and uses the name __run when the current step runs a script without an id. If you use the same script or action more than once in the same job, the name will include a suffix that consists of the sequence number preceded by an underscore. For example, the first script you run will have the name __run, and the second script will be named __run_2. Similarly, the second invocation of actions/checkout will be actionscheckout2.");
        result.put("GITHUB_ACTION_PATH", "The path where an action is located. This property is only supported in composite actions. You can use this path to access files located in the same repository as the action. For example, /home/runner/work/_actions/repo-owner/name-of-action-repo/v1.");
        result.put("GITHUB_ACTION_REPOSITORY", "For a step executing an action, this is the owner and repository name of the action. For example, actions/checkout.");
        result.put("GITHUB_ACTIONS", "Always set to true when GitHub Actions is running the workflow. You can use this variable to differentiate when tests are being run locally or by GitHub Actions.");
        result.put("GITHUB_ACTOR", "The name of the person or app that initiated the workflow. For example, octocat.");
        result.put("GITHUB_ACTOR_ID", "The account ID of the person or app that triggered the initial workflow run. For example, 1234567. Note that this is different from the actor username.");
        result.put("GITHUB_API_URL", "Returns the API URL. For example: https://api.github.com.");
        result.put("GITHUB_BASE_REF", "The name of the base ref or target branch of the pull request in a workflow run. This is only set when the event that triggers a workflow run is either pull_request or pull_request_target. For example, main.");
        result.put("GITHUB_ENV", "The path on the runner to the file that sets variables from workflow commands. This file is unique to the current step and changes for each step in a job. For example, /home/runner/work/_temp/_runner_file_commands/set_env_87406d6e-4979-4d42-98e1-3dab1f48b13a. For more information, see \"Workflow commands for GitHub Actions.\"");
        result.put("GITHUB_EVENT_NAME", "The name of the event that triggered the workflow. For example, workflow_dispatch.");
        result.put("GITHUB_EVENT_PATH", "The path to the file on the runner that contains the full event webhook payload. For example, /github/workflow/event.json.");
        result.put("GITHUB_GRAPHQL_URL", "Returns the GraphQL API URL. For example: https://api.github.com/graphql");
        result.put("GITHUB_HEAD_REF", "The head ref or source branch of the pull request in a workflow run. This property is only set when the event that triggers a workflow run is either pull_request or pull_request_target. For example, feature-branch-1.");
        result.put("GITHUB_JOB", "The job_id of the current job. For example, greeting_job.");
        result.put("GITHUB_PATH", "The path on the runner to the file that sets system PATH variables from workflow commands. This file is unique to the current step and changes for each step in a job. For example, /home/runner/work/_temp/_runner_file_commands/add_path_899b9445-ad4a-400c-aa89-249f18632cf5. For more information, see \"Workflow commands for GitHub Actions.\"");
        result.put("GITHUB_REF", "The fully-formed ref of the branch or tag that triggered the workflow run. For workflows triggered by push, this is the branch or tag ref that was pushed. For workflows triggered by pull_request, this is the pull request merge branch. For workflows triggered by release, this is the release tag created. For other triggers, this is the branch or tag ref that triggered the workflow run. This is only set if a branch or tag is available for the event type. The ref given is fully-formed, meaning that for branches the format is refs/heads/<branch_name>, for pull requests it is refs/pull/<pr_number>/merge, and for tags it is refs/tags/<tag_name>. For example, refs/heads/feature-branch-1.");
        result.put("GITHUB_REF_NAME", "The short ref name of the branch or tag that triggered the workflow run. This value matches the branch or tag name shown on GitHub. For example, feature-branch-1.");
        result.put("GITHUB_REF_PROTECTED", "true if branch protections are configured for the ref that triggered the workflow run.");
        result.put("GITHUB_REF_TYPE", "The type of ref that triggered the workflow run. Valid values are branch or tag.");
        result.put("GITHUB_REPOSITORY", "The owner and repository name. For example, octocat/Hello-World.");
        result.put("GITHUB_REPOSITORY_ID", "The ID of the repository. For example, 123456789. Note that this is different from the repository name.");
        result.put("GITHUB_REPOSITORY_OWNER", "The repository owner's account ID. For example, 1234567. Note that this is different from the owner's name.");
        result.put("GITHUB_RETENTION_DAYS", "The number of days that workflow run logs and artifacts are kept. For example, 90.");
        result.put("GITHUB_RUN_ATTEMPT", "A unique number for each attempt of a particular workflow run in a repository. This number begins at 1 for the workflow run's first attempt, and increments with each re-run. For example, 3.");
        result.put("GITHUB_RUN_ID", "A unique number for each workflow run within a repository. This number does not change if you re-run the workflow run. For example, 1658821493.");
        result.put("GITHUB_RUN_NUMBER", "A unique number for each run of a particular workflow in a repository. This number begins at 1 for the workflow's first run, and increments with each new run. This number does not change if you re-run the workflow run. For example, 3.");
        result.put("GITHUB_SERVER_URL", "The URL of the GitHub server. For example: https://github.com.");
        result.put("GITHUB_SHA", "The commit SHA that triggered the workflow. The value of this commit SHA depends on the event that triggered the workflow. For more information, see \"Events that trigger workflows.\" For example, ffac537e6cbbf934b08745a378932722df287a53.");
        result.put("GITHUB_STEP_SUMMARY", "The path on the runner to the file that contains job summaries from workflow commands. This file is unique to the current step and changes for each step in a job. For example, /home/runner/_layout/_work/_temp/_runner_file_commands/step_summary_1cb22d7f-5663-41a8-9ffc-13472605c76c. For more information, see \"Workflow commands for GitHub Actions.\"");
        result.put("GITHUB_WORKFLOW", "The name of the workflow. For example, My test workflow. If the workflow file doesn't specify a name, the value of this variable is the full path of the workflow file in the repository.");
        result.put("GITHUB_WORKFLOW_REF", "The ref path to the workflow. For example, octocat/hello-world/.github/workflows/my-workflow.yml@refs/heads/my_branch.");
        result.put("GITHUB_WORKFLOW_SHA", "The commit SHA for the workflow file.");
        result.put("GITHUB_WORKSPACE", "The default working directory on the runner for steps, and the default location of your repository when using the checkout action. For example, /home/runner/work/my-repo-name/my-repo-name.");
        result.put("RUNNER_ARCH", "The architecture of the runner executing the job. Possible values are X86, X64, ARM, or ARM64.");
        result.put("RUNNER_DEBUG", "This is set only if debug logging is enabled, and always has the value of 1. It can be useful as an indicator to enable additional debugging or verbose logging in your own job steps.");
        result.put("RUNNER_NAME", "The name of the runner executing the job. For example, Hosted Agent");
        result.put("RUNNER_OS", "The operating system of the runner executing the job. Possible values are Linux, Windows, or macOS. For example, Windows");
        result.put("RUNNER_TEMP", "The path to a temporary directory on the runner. This directory is emptied at the beginning and end of each job. Note that files will not be removed if the runner's user account does not have permission to delete them. For example, D:\\a\\_temp");
        result.put("RUNNER_TOOL_CACHE", "The path to the directory containing preinstalled tools for GitHub-hosted runners. For more information, see \"About GitHub-hosted runners\". For example, C:\\hostedtoolcache\\windows");
        return result;
    }

    private GitHubWorkflowConfig() {
    }
}
