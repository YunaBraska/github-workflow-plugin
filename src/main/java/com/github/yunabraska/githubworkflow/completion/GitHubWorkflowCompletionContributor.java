package com.github.yunabraska.githubworkflow.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.mapToLookupElements;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.orEmpty;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.toGithubOutputs;
import static com.github.yunabraska.githubworkflow.completion.WorkflowContext.getCaretBracketItem;
import static java.util.Optional.ofNullable;

/**
 * @author jansorg
 */
public class GitHubWorkflowCompletionContributor extends CompletionContributor {

    public GitHubWorkflowCompletionContributor() {
        // completions for plain text files
//        extend(CompletionType.BASIC, PlatformPatterns.psiElement(PlainTextTokenTypes.PLAIN_TEXT), completionProvider());

        // completions for content of string literals
//        extend(CompletionType.BASIC, PlatformPatterns.psiElement().with(new GitHubWorkFlowFilePattern()), completionProvider());

        // always suggest when invoked manually
//        extend(CompletionType.BASIC, PlatformPatterns.not(PlatformPatterns.alwaysFalse()), completionProvider());

        // all events?
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    @NotNull
    private static CompletionProvider<CompletionParameters> completionProvider() {
        return new CompletionProvider<>() {

            public void addCompletions(
                    @NotNull final CompletionParameters parameters,
                    @NotNull final ProcessingContext context,
                    @NotNull final CompletionResultSet resultSet
            ) {

                //TODO: set file schema when actual is not present
                final Optional<String> actual = ((JsonSchemaServiceImpl) JsonSchemaService.Impl.get(parameters.getOriginalFile().getProject())).getProvidersForFile(parameters.getPosition().getContainingFile().getOriginalFile().getViewProvider().getVirtualFile()).stream().map(JsonSchemaFileProvider::getName).filter("GitHub Workflow"::equals).findFirst();
                final Optional<String> expected = JsonSchemaService.Impl.get(parameters.getOriginalFile().getProject()).getAllUserVisibleSchemas().stream().map(JsonSchemaInfo::getName).filter("GitHub Workflow"::equals).findFirst();
                System.out.println("FILE SCHEMA [" + actual.orElse(null) + "] [" + expected.orElse(null) + "]");

                getWorkflowFile(parameters.getPosition()).ifPresent(path -> {
                    final int caretOffset = parameters.getOffset();
                    final String wholeText = parameters.getOriginalFile().getText();
                    final int endIndex = Math.max(wholeText.lastIndexOf("\n", caretOffset), wholeText.lastIndexOf("\r", caretOffset));
                    final WorkflowFile workflowFile = WorkflowFile.workflowFileOf(path.toString(), wholeText.substring(0, endIndex != -1 ? endIndex : caretOffset));

                    //TODO: needs: job list
                    final Optional<String[]> caretBracketItem = getCaretBracketItem(wholeText, caretOffset);
                    caretBracketItem.ifPresent(cbi -> {
                        final CompletionResultSet resultSetForced = resultSet.withPrefixMatcher(PrefixMatcher.ALWAYS_TRUE);
                        if (cbi.length == 1) {
                            switch (cbi[0]) {
                                case "":
                                    ofNullable(DEFAULT_VALUE_MAP.getOrDefault("${{}}", null)).map(Supplier::get).ifPresent(resultSetForced::addAllElements);
                                    break;
                                case FIELD_STEPS:
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_STEPS,
                                            input -> input.getChild("id").isPresent(),
                                            n -> n.getChild("id").map(YamlNode::value).orElse(""),
                                            n -> n.getChild("uses").map(YamlNode::value).orElseGet(() -> n.getChild("name").map(YamlNode::value).orElse(""))
                                    ));
                                    break;
                                case FIELD_JOBS:
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_JOBS,
                                            job -> job.name() != null && job.getChild(FIELD_OUTPUTS).isPresent(),
                                            n -> orEmpty(n.name()),
                                            n -> n.getChild("name").map(YamlNode::value).orElse("")
                                    ));
                                    break;
                                case FIELD_ENVS:
                                    //TODO: Envs from echo and step.env
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_ENVS,
                                            env -> env.name() != null,
                                            n -> orEmpty(n.name()),
                                            n -> ofNullable(n.value()).orElse("")
                                    ));
                                    break;
                                case FIELD_INPUTS:
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_INPUTS,
                                            input -> input.name() != null,
                                            n -> orEmpty(n.name()),
                                            GitHubWorkflowUtils::getDescription
                                    ));
                                    break;
                                case FIELD_SECRETS:
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_SECRETS,
                                            secret -> secret.name() != null,
                                            n -> orEmpty(n.name()),
                                            GitHubWorkflowUtils::getDescription
                                    ));
                                    break;
                                default:
                                    break;
                            }
                            ofNullable(DEFAULT_VALUE_MAP.getOrDefault(cbi[0], null)).map(Supplier::get).ifPresent(resultSetForced::addAllElements);
                        } else if (cbi.length == 2 && (FIELD_STEPS.equals(cbi[0]) || FIELD_JOBS.equals(cbi[0]))) {
                            resultSetForced.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(FIELD_OUTPUTS).withIcon(AllIcons.General.Add).withBoldness(true).withTypeText("output values of " + cbi[0]), 5));
                        } else if (cbi.length == 3 && FIELD_STEPS.equals(cbi[0])) {
                            workflowFile.getActionOutputs(cbi[1]).ifPresent(map -> resultSetForced.addAllElements(mapToLookupElements(map, 5, true, ':')));
                            workflowFile.getStepById(cbi[1]).ifPresent(step -> {
                                step.getChild("uses").map(YamlNode::value).map(GitHubAction::getGitHubAction).map(GitHubAction::outputs).ifPresent(map -> resultSetForced.addAllElements(mapToLookupElements(map, 5, true)));
                                final Map<String, String> githubOutputs = toGithubOutputs(step);
                                if (!githubOutputs.isEmpty()) {
                                    resultSetForced.addAllElements(mapToLookupElements(githubOutputs, 5, true));
                                }
                            });
                        } else if (cbi.length == 3 && FIELD_JOBS.equals(cbi[0])) {
                            resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                    FIELD_OUTPUTS, output ->
                                            output.value() != null
                                                    && output.parent() != null
                                                    && output.parent().parent() != null
                                                    && output.parent().parent().parent() != null
                                                    && cbi[1].equals(output.parent().parent().name())
                                                    && FIELD_JOBS.equals(output.parent().parent().parent().name())
                                    ,
                                    n -> orEmpty(n.name()),
                                    //TODO: resolve to step outputs (from actions, workflows and echo "has_changes=$has_changes" >> $GITHUB_OUTPUT)
                                    n -> orEmpty(n.value())
                            ));
                        }
                    });
                    //ACTIONS && WORKFLOWS
                    if (!caretBracketItem.isPresent()) {
                        workflowFile.getUses().ifPresent(map -> resultSet.addAllElements(mapToLookupElements(map, 5, true, ':')));
                    }
                });
            }
        };
    }
}
