package com.github.yunabraska.githubworkflow.completion;

import com.github.yunabraska.githubworkflow.model.WorkflowContext;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.completion.CompletionItem.*;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.addLookupElements;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getDefaultPrefix;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.completion.NodeIcon.ICON_RUNNER;
import static com.github.yunabraska.githubworkflow.model.WorkflowContext.WORKFLOW_CONTEXT_MAP;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class GitHubWorkflowCompletionContributor extends CompletionContributor {

    public GitHubWorkflowCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    @NotNull
    private static CompletionProvider<CompletionParameters> completionProvider() {
        return new CompletionProvider() {
            @Override
            public void addCompletions(
                    @NotNull final CompletionParameters parameters,
                    @NotNull final ProcessingContext processingContext,
                    @NotNull final CompletionResultSet resultSet
            ) {
                getWorkflowFile(parameters.getPosition()).map(Path::toString).map(WORKFLOW_CONTEXT_MAP::get).ifPresent(context -> {
                    context.position(parameters.getOffset());
                    final String[] prefix = new String[]{""};
                    final Optional<String[]> caretBracketItem = ofNullable(context.position()).map(pos -> getCaretBracketItem(pos, parameters.getOffset(), prefix)).orElseGet(() -> Optional.of(prefix));
                    caretBracketItem.ifPresent(cbi -> {
                        final Map<Integer, List<CompletionItem>> completionResultMap = new HashMap<>();
                        for (int i = 0; i < cbi.length; i++) {
                            //DON'T AUTO COMPLETE WHEN PREVIOUS ITEM IS NOT VALID
                            final List<CompletionItem> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
                            final int index = i;
                            if (i != 0 && (previousCompletions.isEmpty() || previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index])))) {
                                return;
                            } else {
                                addCompletionItems(cbi, i, context, completionResultMap);
                            }
                        }
                        //ADD LOOKUP ELEMENTS
                        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                                .map(GitHubWorkflowCompletionContributor::toLookupItems)
                                .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, prefix[0], lookupElements));
                    });
                    //ACTIONS && WORKFLOWS
                    if (!caretBracketItem.isPresent()) {
                        if (context.position().findParent(FIELD_NEEDS).isPresent()) {
                            //[jobs.job_name.needs] list previous jobs
                            Optional.of(listNeeds(context.position())).filter(cil -> !cil.isEmpty())
                                    .map(GitHubWorkflowCompletionContributor::toLookupItems)
                                    .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, getDefaultPrefix(parameters), lookupElements));
                        } else {
                            //[jobs.job_id.steps.step_id:with]
                            final Optional<Map<String, String>> withCompletion = context.position().findParentWith()
                                    .flatMap(YamlElement::findParentStep)
                                    .flatMap(step -> step.child(FIELD_USES))
                                    .map(YamlElement::textOrChildTextNoQuotes)
                                    .map(GitHubAction::getGitHubAction)
                                    .map(GitHubAction::inputs);
                            withCompletion.ifPresent(map -> addLookupElements(resultSet.withPrefixMatcher(getDefaultPrefix(parameters)), map, NodeIcon.ICON_INPUT, ':'));
                        }
                    }
                });
            }
        };
    }

    private static void addElementsWithPrefix(final CompletionResultSet resultSet, final String prefix, final List<LookupElement> lookupElements) {
        resultSet.withPrefixMatcher(new CamelHumpMatcher(prefix)).addAllElements(lookupElements);
    }

    private static void addCompletionItems(final String[] cbi, final int i, final WorkflowContext context, final Map<Integer, List<CompletionItem>> completionItemMap) {
        if (i == 0) {
            switch (cbi[0]) {
                case FIELD_STEPS:
                    completionItemMap.put(i, listSteps(context.position()));
                    break;
                case FIELD_JOBS:
                    completionItemMap.put(i, listJobs(context.position()));
                    break;
                case FIELD_ENVS:
                    completionItemMap.put(i, listEnvs(context.position(), context.cursorAbs()));
                    break;
                case FIELD_GITHUB:
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get(), ICON_ENV));
                    break;
                case FIELD_RUNNER:
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_RUNNER).get(), ICON_RUNNER));
                    break;
                case FIELD_INPUTS:
                    completionItemMap.put(i, listInputs(context.position()));
                    break;
                case FIELD_SECRETS:
                    completionItemMap.put(i, listSecrets(context.position()));
                    break;
                case FIELD_NEEDS:
                    completionItemMap.put(i, listJobNeeds(context.position()));
                    break;
                default:
                    //SHOW ONLY JOBS [on.workflow_call.outputs.key.value:xxx]
                    if (context.position().findParentOutput().map(YamlElement::findParentOn).isPresent()) {
                        completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                    } else if (!context.position().findParent("runs-on").isPresent() && !context.position().findParent("os").isPresent()) {
                        //DEFAULT
                        ofNullable(DEFAULT_VALUE_MAP.getOrDefault(FIELD_DEFAULT, null))
                                .map(Supplier::get)
                                .map(map -> {
                                    final Map<String, String> copyMap = new HashMap<>(map);
                                    //'JOBS' HAS ONLY ONE PLACE
                                    copyMap.remove(FIELD_JOBS);
                                    //IF NO 'NEEDS' IS DEFINED
                                    if (!context.position().findParentJob().map(job -> job.child(FIELD_NEEDS)).isPresent()) {
                                        copyMap.remove(FIELD_NEEDS);
                                    }
                                    return copyMap;
                                })
                                .map(map -> completionItemsOf(map, ICON_NODE))
                                .ifPresent(items -> completionItemMap.put(i, items));
                    }
                    break;
            }
        } else if (i == 1) {
            switch (cbi[0]) {
                case FIELD_JOBS:
                case FIELD_NEEDS:
                case FIELD_STEPS:
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_OUTPUTS, "", ICON_OUTPUT)));
                    break;
                default:
                    break;
            }
        } else if (i == 2) {
            switch (cbi[0]) {
                case FIELD_JOBS:
                case FIELD_NEEDS:
                    completionItemMap.put(i, listJobOutputs(context.position(), cbi[1]));
                    break;
                case FIELD_STEPS:
                    completionItemMap.put(i, listStepOutputs(context.position(), context.cursorAbs(), cbi[1]));
                    break;
                default:
                    break;
            }
        }
    }

    @NotNull
    private static List<LookupElement> toLookupItems(final List<CompletionItem> items) {
        return items.stream().map(CompletionItem::toLookupElement).collect(Collectors.toList());
    }
}
