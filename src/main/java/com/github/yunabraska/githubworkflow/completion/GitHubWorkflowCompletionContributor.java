package com.github.yunabraska.githubworkflow.completion;

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
import java.util.concurrent.atomic.AtomicReference;
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
                    @NotNull final ProcessingContext context,
                    @NotNull final CompletionResultSet resultSet
            ) {
                getWorkflowFile(parameters.getPosition()).ifPresent(path -> {
                    //CACHE USE ONLY ON NEED
                    final AtomicReference<WorkflowFile> partCache = new AtomicReference<>(null);
                    final AtomicReference<WorkflowFile> fullCache = new AtomicReference<>(null);
                    final Supplier<WorkflowFile> partFile = fromPartCache(partCache, path, parameters);
                    final Supplier<WorkflowFile> fullFile = fromFullCache(partCache, fullCache, path, parameters);


                    final String[] prefix = new String[]{""};
                    final Optional<String[]> caretBracketItem = getCaretBracketItem(parameters, partFile, prefix);
                    caretBracketItem.ifPresent(cbi -> {
                        final Map<Integer, List<CompletionItem>> completionResultMap = new HashMap<>();
                        for (int i = 0; i < cbi.length; i++) {
                            //DON'T AUTO COMPLETE WHEN PREVIOUS ITEM IS NOT VALID
                            final List<CompletionItem> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
                            final int index = i;
                            if (i != 0 && (previousCompletions.isEmpty() || previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index])))) {
                                return;
                            } else {
                                addCompletionItems(cbi, i, partFile, fullFile, completionResultMap);
                            }
                        }
                        //ADD LOOKUP ELEMENTS
                        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                                .map(GitHubWorkflowCompletionContributor::toLookupItems)
                                .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, prefix[0], lookupElements));
                    });
                    //ACTIONS && WORKFLOWS
                    if (!caretBracketItem.isPresent()) {
                        if (FIELD_NEEDS.equals(partFile.get().getCurrentNode().name())) {
                            Optional.of(listNeeds(partFile, fullFile)).filter(cil -> !cil.isEmpty())
                                    .map(GitHubWorkflowCompletionContributor::toLookupItems)
                                    .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, getDefaultPrefix(parameters), lookupElements));
                        } else {
                            partFile.get().getActionInputs()
                                    .ifPresent(map -> addLookupElements(resultSet.withPrefixMatcher(getDefaultPrefix(parameters)), map, NodeIcon.ICON_INPUT, ':'));
                        }
                    }
                });
            }

            private Supplier<WorkflowFile> fromPartCache(final AtomicReference<WorkflowFile> partCache, final Path path, final CompletionParameters parameters) {
                return () -> {
                    if (partCache.get() == null) {
                        final int caretOffset = parameters.getOffset();
                        final String wholeText = parameters.getOriginalFile().getText();
                        final int endIndex = Math.max(wholeText.indexOf("\n", caretOffset), wholeText.indexOf("\r", caretOffset));
                        partCache.set(WorkflowFile.workflowFileOf("part_" + path, wholeText.substring(0, endIndex != -1 ? endIndex : caretOffset)));
                    }
                    return partCache.get();
                };
            }

            private Supplier<WorkflowFile> fromFullCache(final AtomicReference<WorkflowFile> partCache, final AtomicReference<WorkflowFile> fullCache, final Path path, final CompletionParameters parameters) {
                return () -> {
                    if (fullCache.get() == null) {
                        fullCache.set(ofNullable(WorkflowFile.workflowFileOf("complete_" + path, parameters.getOriginalFile().getText())).orElse(fromPartCache(partCache, path, parameters).get()));
                    }
                    return fullCache.get();
                };
            }
        };
    }

    private static void addElementsWithPrefix(final CompletionResultSet resultSet, final String prefix, final List<LookupElement> lookupElements) {
        resultSet.withPrefixMatcher(new CamelHumpMatcher(prefix)).addAllElements(lookupElements);
    }

    private static void addCompletionItems(final String[] cbi, final int i, final Supplier<WorkflowFile> partFile, final Supplier<WorkflowFile> fullFile, final Map<Integer, List<CompletionItem>> completionItemMap) {
        if (i == 0) {
            switch (cbi[0]) {
                case FIELD_STEPS:
                    completionItemMap.put(i, listSteps(partFile, fullFile));
                    break;
                case FIELD_JOBS:
                    completionItemMap.put(i, listJobs(partFile, fullFile));
                    break;
                case FIELD_ENVS:
                    completionItemMap.put(i, listEnvs(partFile, fullFile));
                    break;
                case FIELD_GITHUB:
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get(), ICON_ENV));
                    break;
                case FIELD_INPUTS:
                    completionItemMap.put(i, listInputs(partFile, fullFile));
                    break;
                case FIELD_SECRETS:
                    completionItemMap.put(i, listSecrets(partFile, fullFile));
                    break;
                case FIELD_NEEDS:
                    completionItemMap.put(i, listJobNeeds(partFile, fullFile));
                    break;
                default:
                    //ON.workflow_call.outputs
                    if (partFile.get().isOutputTriggerNode()) {
                        completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                    } else if (!"runs-on".equals(partFile.get().getCurrentNode().name()) && !"os".equals(partFile.get().getCurrentNode().name())) {
                        //DEFAULT
                        ofNullable(DEFAULT_VALUE_MAP.getOrDefault(FIELD_DEFAULT, null))
                                .map(Supplier::get)
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
                    completionItemMap.put(i, listJobOutputs(cbi[1], partFile, fullFile));
                    break;
                case FIELD_STEPS:
                    completionItemMap.put(i, listStepOutputs(cbi[1], partFile, fullFile));
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
