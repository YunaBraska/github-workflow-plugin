package com.github.yunabraska.githubworkflow.completion;

import com.github.yunabraska.githubworkflow.config.NodeIcon;
import com.github.yunabraska.githubworkflow.model.CompletionItem;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.YamlElement;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.addLookupElements;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getDefaultPrefix;
import static com.github.yunabraska.githubworkflow.completion.GitHubWorkflowUtils.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.ICON_RUNNER;
import static com.github.yunabraska.githubworkflow.model.CompletionItem.*;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.yamlOf;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class CodeCompletionService extends CompletionContributor {

    public CodeCompletionService() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    @NotNull
    private static CompletionProvider<CompletionParameters> completionProvider() {
        return new CompletionProvider<>() {
            @Override
            public void addCompletions(
                    @NotNull final CompletionParameters parameters,
                    @NotNull final ProcessingContext processingContext,
                    @NotNull final CompletionResultSet resultSet
            ) {
                getWorkflowFile(parameters.getPosition()).map(path -> yamlOf(parameters.getPosition())).map(YamlElement::context).ifPresent(context -> {
                    final Project project = Optional.of(parameters.getOriginalFile()).map(PsiElement::getProject).orElse(null);
                    final int offset = parameters.getOffset();
                    final YamlElement position = context.getClosestElement(offset).orElse(new YamlElement(-1, -1, null, null, null, null));
                    final String[] prefix = new String[]{""};
                    final Optional<String[]> caretBracketItem = Optional.of(position).filter(p -> p.startIndexAbs() > -1).map(pos -> getCaretBracketItem(pos, offset, prefix)).orElseGet(() -> Optional.of(prefix));
                    caretBracketItem.ifPresent(cbi -> {
                        final Map<Integer, List<CompletionItem>> completionResultMap = new HashMap<>();
                        for (int i = 0; i < cbi.length; i++) {
                            //DON'T AUTO COMPLETE WHEN PREVIOUS ITEM IS NOT VALID
                            final List<CompletionItem> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
                            final int index = i;
                            if (i != 0 && (previousCompletions.isEmpty() || previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index])))) {
                                return;
                            } else {
                                addCompletionItems(project, cbi, i, offset, position, completionResultMap);
                            }
                        }
                        //ADD LOOKUP ELEMENTS
                        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                                .map(CodeCompletionService::toLookupItems)
                                .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, prefix[0], lookupElements));
                    });
                    //ACTIONS && WORKFLOWS
                    if (caretBracketItem.isEmpty()) {
                        if (position.findParent(FIELD_NEEDS).isPresent()) {
                            //[jobs.job_name.needs] list previous jobs
                            Optional.of(listNeeds(position)).filter(cil -> !cil.isEmpty())
                                    .map(CodeCompletionService::toLookupItems)
                                    .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, getDefaultPrefix(parameters), lookupElements));
                        } else {
                            //USES COMPLETION [jobs.job_id.steps.step_id:with]
                            final Optional<Map<String, String>> withCompletion = position.findParentWith()
                                    .map(YamlElement::parent)
                                    .flatMap(step -> step.child(FIELD_USES))
                                    .map(YamlElement::textOrChildTextNoQuotes)
                                    .map(GitHubAction::getGitHubAction)
                                    .map(action -> action.inputs(project));
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

    private static void addCompletionItems(final Project project, final String[] cbi, final int i, final int offset, final YamlElement position, final Map<Integer, List<CompletionItem>> completionItemMap) {
        if (i == 0) {
            handleFirstItem(cbi, i, offset, position, completionItemMap);
        } else if (i == 1) {
            handleSecondItem(cbi, i, completionItemMap);
        } else if (i == 2) {
            handleThirdItem(project, cbi, i, offset, position, completionItemMap);
        }
    }

    private static void handleThirdItem(final Project project, final String[] cbi, final int i, final int offset, final YamlElement position, final Map<Integer, List<CompletionItem>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_JOBS, FIELD_NEEDS -> completionItemMap.put(i, listJobOutputs(project, position, cbi[1]));
            case FIELD_STEPS -> completionItemMap.put(i, listStepOutputs(project, position, offset, cbi[1]));
            default -> {
                // ignored
            }
        }
    }

    private static void handleSecondItem(final String[] cbi, final int i, final Map<Integer, List<CompletionItem>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_JOBS, FIELD_NEEDS, FIELD_STEPS ->
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_OUTPUTS, "", ICON_OUTPUT)));
            default -> {
                // ignored
            }
        }
    }

    private static void handleFirstItem(final String[] cbi, final int i, final int offset, final YamlElement position, final Map<Integer, List<CompletionItem>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, listSteps(position));
            case FIELD_JOBS -> completionItemMap.put(i, listJobs(position));
            case FIELD_ENVS -> completionItemMap.put(i, listEnvs(position, offset));
            case FIELD_GITHUB ->
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get(), ICON_ENV));
            case FIELD_RUNNER ->
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_RUNNER).get(), ICON_RUNNER));
            case FIELD_INPUTS -> completionItemMap.put(i, listInputs(position));
            case FIELD_SECRETS -> completionItemMap.put(i, listSecrets(position));
            case FIELD_NEEDS -> completionItemMap.put(i, listJobNeeds(position));
            default -> {
                //SHOW ONLY JOBS [on.workflow_call.outputs.key.value:xxx]
                if (position.findParentOutput().map(YamlElement::findParentOn).isPresent()) {
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                } else if (position.findParent("runs-on").isEmpty() && position.findParent("os").isEmpty()) {
                    //DEFAULT
                    ofNullable(DEFAULT_VALUE_MAP.getOrDefault(FIELD_DEFAULT, null))
                            .map(Supplier::get)
                            .map(map -> {
                                final Map<String, String> copyMap = new HashMap<>(map);
                                //'JOBS' HAS ONLY ONE PLACE
                                copyMap.remove(FIELD_JOBS);
                                //IF NO 'NEEDS' IS DEFINED
                                if (position.findParentJob().map(job -> job.child(FIELD_NEEDS)).isEmpty()) {
                                    copyMap.remove(FIELD_NEEDS);
                                }
                                return copyMap;
                            })
                            .map(map -> completionItemsOf(map, ICON_NODE))
                            .ifPresent(items -> completionItemMap.put(i, items));
                }
            }
        }
    }

    @NotNull
    private static List<LookupElement> toLookupItems(final List<CompletionItem> items) {
        return items.stream().map(CompletionItem::toLookupElement).toList();
    }
}
