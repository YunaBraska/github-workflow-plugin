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
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.addLookupElements;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.orEmpty;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.toGithubOutputs;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_STEP;
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
                getWorkflowFile(parameters.getPosition()).ifPresent(path -> {
                    final int caretOffset = parameters.getOffset();
                    final String wholeText = parameters.getOriginalFile().getText();
                    final int endIndex = Math.max(wholeText.indexOf("\n", caretOffset), wholeText.indexOf("\r", caretOffset));
                    final WorkflowFile workflowFileComplete = WorkflowFile.workflowFileOf("complete_" + path, wholeText);
                    final WorkflowFile workflowFile = WorkflowFile.workflowFileOf("part_" + path, wholeText.substring(0, endIndex != -1 ? endIndex : caretOffset));
//                    final Optional<YamlNode> jobNode = workflowFile.getParent(node -> node.hasParent(FIELD_JOBS));
//                    final Optional<YamlNode> stepNode = workflowFile.getParent(node -> node.hasParent(FIELD_STEPS));

                    //TODO: needs: job list
                    //TODO: validate every cbi item
                    final CompletionResultSet resultSetForced = resultSet.withPrefixMatcher(PrefixMatcher.ALWAYS_TRUE);
                    final Optional<String[]> caretBracketItem = getCaretBracketItem(wholeText, caretOffset, workflowFile::getCurrentNode);
                    caretBracketItem.ifPresent(cbi -> {

                        if (cbi.length == 1) {
                            switch (cbi[0]) {
                                case FIELD_STEPS:
                                    final WorkflowFile workflowFile1 = Optional.of(workflowFile.getCurrentNode()).map(YamlNode::parent).filter(parent -> FIELD_OUTPUTS.equals(parent.name())).map(YamlNode::parent).filter(parent -> FIELD_JOBS.equals(parent.name())).map(n -> workflowFileComplete).orElse(workflowFile);
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_STEPS,
                                            input -> input.getChild("id").isPresent(),
                                            n -> n.getChild("id").map(YamlNode::value).orElse(""),
                                            n -> n.getChild("uses").map(YamlNode::value).orElseGet(() -> n.getChild("name").map(YamlNode::value).orElse("")),
                                            ICON_STEP
//                                            '.'
                                    ));
                                    break;
                                case FIELD_JOBS:
                                    addJobNames(workflowFile, resultSetForced);
                                    break;
                                case FIELD_ENVS:
                                    //TODO: Envs from echo and step.env
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_ENVS,
                                            env -> env.name() != null,
                                            n -> orEmpty(n.name()),
                                            n -> ofNullable(n.value()).orElse(""),
                                            NodeIcon.ICON_INPUT
                                    ));
                                    break;
                                case FIELD_INPUTS:
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_INPUTS,
                                            input -> input.name() != null,
                                            n -> orEmpty(n.name()),
                                            GitHubWorkflowUtils::getDescription,
                                            NodeIcon.ICON_INPUT
                                    ));
                                    break;
                                case FIELD_SECRETS:
                                    resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                                            FIELD_SECRETS,
                                            secret -> secret.name() != null,
                                            n -> orEmpty(n.name()),
                                            GitHubWorkflowUtils::getDescription,
                                            NodeIcon.ICON_SECRET
                                    ));
                                    break;
                                default:
                                    ofNullable(DEFAULT_VALUE_MAP.getOrDefault("${{}}", null)).map(Supplier::get).ifPresent(resultSetForced::addAllElements);
                            }
                            ofNullable(DEFAULT_VALUE_MAP.getOrDefault(cbi[0], null)).map(Supplier::get).ifPresent(resultSetForced::addAllElements);
                        } else if ((cbi.length == 2 && (FIELD_STEPS.equals(cbi[0]) || FIELD_JOBS.equals(cbi[0])))
                                || (cbi.length == 3 && !FIELD_OUTPUTS.equals(cbi[2]))) {
                            resultSetForced.addElement(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(FIELD_OUTPUTS).withIcon(AllIcons.Nodes.Method).withBoldness(true).withTypeText("output values of " + cbi[0]), 5));
                        } else if (cbi.length == 3 && FIELD_STEPS.equals(cbi[0])) {
                            workflowFile.getActionOutputs(cbi[1]).ifPresent(map -> addLookupElements(resultSetForced, map, NodeIcon.ICON_OUTPUT));
//                            workflowFile.getActionOutputs(cbi[1]).ifPresent(map -> addLookupElements(resultSetForced, map, NodeIcon.ICON_OUTPUT, '.'));
                            workflowFile.getStepById(cbi[1]).ifPresent(step -> addLookupElements(resultSetForced, toGithubOutputs(step), NodeIcon.ICON_GITHUB_OUTPUT));
//                            workflowFile.getStepById(cbi[1]).ifPresent(step -> addLookupElements(resultSetForced, toGithubOutputs(step), NodeIcon.ICON_GITHUB_OUTPUT, '.'));
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
                                    n -> orEmpty(n.value()),
                                    NodeIcon.ICON_OUTPUT
                            ));
                        }
                    });
                    //ACTIONS && WORKFLOWS
                    if (!caretBracketItem.isPresent()) {
                        if ("needs".equals(workflowFile.getCurrentNode().name())) {
                            addJobNames(workflowFile, resultSetForced);
                        } else {
                            workflowFile.getActionInputs().ifPresent(map -> addLookupElements(resultSet, map, NodeIcon.ICON_INPUT, ':'));
                        }
                    }
                });
            }
        };
    }

    private static void addJobNames(final WorkflowFile workflowFile, final CompletionResultSet resultSetForced) {
        resultSetForced.addAllElements(workflowFile.nodesToLookupElement(
                FIELD_JOBS,
                job -> job.name() != null && job.getChild(FIELD_OUTPUTS).isPresent(),
                n -> orEmpty(n.name()),
                n -> n.getChild("name").map(YamlNode::value).orElse(""),
                ICON_JOB
//                                            '.'
        ));
    }
}
