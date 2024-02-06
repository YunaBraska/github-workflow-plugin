package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.logic.Steps;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_CONCLUSION;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_DEFAULT_MAP;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_GITHUB;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTCOME;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUNNER;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getStartIndex;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.toLookupElement;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.logic.Envs.listEnvs;
import static com.github.yunabraska.githubworkflow.logic.GitHub.codeCompletionGithub;
import static com.github.yunabraska.githubworkflow.logic.Inputs.listInputs;
import static com.github.yunabraska.githubworkflow.logic.Jobs.codeCompletionJobs;
import static com.github.yunabraska.githubworkflow.logic.Jobs.listJobs;
import static com.github.yunabraska.githubworkflow.logic.Needs.codeCompletionNeeds;
import static com.github.yunabraska.githubworkflow.logic.Needs.listJobNeeds;
import static com.github.yunabraska.githubworkflow.logic.Runner.codeCompletionRunner;
import static com.github.yunabraska.githubworkflow.logic.Secrets.listSecrets;
import static com.github.yunabraska.githubworkflow.logic.Steps.codeCompletionSteps;
import static com.github.yunabraska.githubworkflow.logic.Steps.listSteps;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class CodeCompletion extends CompletionContributor {

    public CodeCompletion() {
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
                final PsiElement position = parameters.getPosition();
                getWorkflowFile(position).ifPresent(file -> {
                    final int offset = parameters.getOffset();
                    final String[] prefix = new String[]{""};
                    final Optional<String[]> caretBracketItem = offset < 1 ? Optional.of(prefix) : getCaretBracketItem(position, offset, prefix);
                    caretBracketItem.ifPresent(cbi -> addCodeCompletionItems(resultSet, cbi, position, prefix));

                    //ACTIONS && WORKFLOWS
                    if (caretBracketItem.isEmpty()) {
                        if (getParent(position, FIELD_RUN).isPresent() && position.getText().contains("$IntellijIdeaRulezzz")) {
                            // AUTO COMPLETE [$GITHUB_ENV, $GITHUB_OUTPUT]
                            addLookupElements(
                                    resultSet.withPrefixMatcher(prefix[0]),
                                    Map.of("GITHUB_ENV", FIELD_DEFAULT_MAP.getOrDefault(FIELD_ENVS, ""),
                                            "GITHUB_OUTPUT", FIELD_DEFAULT_MAP.getOrDefault(FIELD_GITHUB, "")),
                                    NodeIcon.ICON_ENV,
                                    Character.MIN_VALUE);
                        } else if (getParent(position, FIELD_NEEDS).isPresent()) {
                            //[jobs.job_name.needs] list previous jobs
                            Optional.of(codeCompletionNeeds(position)).filter(cil -> !cil.isEmpty())
                                    .map(CodeCompletion::toLookupItems)
                                    .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, getDefaultPrefix(parameters), lookupElements));
                        } else {
                            //USES COMPLETION [jobs.job_id.steps.step_id:with]
                            final Optional<Map<String, String>> withCompletion = getParentStepOrJob(position)
                                    .flatMap(step -> PsiElementHelper.getChild(step, FIELD_USES))
                                    .map(GitHubActionCache::getAction)
                                    .filter(GitHubAction::isResolved)
                                    .map(GitHubAction::freshInputs);
                            withCompletion.ifPresent(map -> addLookupElements(resultSet.withPrefixMatcher(getDefaultPrefix(parameters)), map, NodeIcon.ICON_INPUT, ':'));
                        }
                    }
                });
            }
        };
    }

    private static void addCodeCompletionItems(final CompletionResultSet resultSet, final String[] cbi, final PsiElement position, final String[] prefix) {
        final Map<Integer, List<SimpleElement>> completionResultMap = new HashMap<>();
        for (int i = 0; i < cbi.length; i++) {
            //DON'T AUTO COMPLETE WHEN PREVIOUS ITEM IS NOT VALID
            final List<SimpleElement> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
            final int index = i;
            if (i != 0 && (previousCompletions.isEmpty() || previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index])))) {
                return;
            } else {
                addCompletionItems(cbi, i, position, completionResultMap);
            }
        }
        //ADD LOOKUP ELEMENTS
        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                .map(CodeCompletion::toLookupItems)
                .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, prefix[0], lookupElements));
    }

    private static void addElementsWithPrefix(final CompletionResultSet resultSet, final String prefix, final List<LookupElement> lookupElements) {
        resultSet.withPrefixMatcher(new CamelHumpMatcher(prefix)).addAllElements(lookupElements);
    }

    private static void addCompletionItems(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        if (i == 0) {
            handleFirstItem(cbi, i, position, completionItemMap);
        } else if (i == 1) {
            handleSecondItem(cbi, i, completionItemMap);
        } else if (i == 2) {
            handleThirdItem(cbi, i, position, completionItemMap);
        }
    }

    private static void handleThirdItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_JOBS -> completionItemMap.put(i, codeCompletionJobs(cbi[1], position));
            case FIELD_NEEDS -> completionItemMap.put(i, codeCompletionNeeds(cbi[1], position));
            case FIELD_STEPS -> completionItemMap.put(i, Steps.codeCompletionSteps(cbi[1], position));
            default -> {
                // ignored
            }
        }
    }

    private static void handleSecondItem(final String[] cbi, final int i, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, List.of(
                    completionItemOf(FIELD_OUTPUTS, "The set of outputs defined for the step.", ICON_OUTPUT),
                    completionItemOf(FIELD_CONCLUSION, "The result of a completed step after continue-on-error is applied.", ICON_OUTPUT),
                    completionItemOf(FIELD_OUTCOME, "The result of a completed step before continue-on-error is applied.", ICON_OUTPUT)
            ));
            case FIELD_JOBS, FIELD_NEEDS -> completionItemMap.put(i, List.of(
                    completionItemOf(FIELD_OUTPUTS, "The set of outputs defined for the step.", ICON_OUTPUT)
            ));
            default -> {
                // ignored
            }
        }
    }

    private static void handleFirstItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, codeCompletionSteps(position));
            case FIELD_JOBS -> completionItemMap.put(i, codeCompletionJobs(position));
            case FIELD_ENVS -> completionItemMap.put(i, listEnvs(position));
            case FIELD_GITHUB -> completionItemMap.put(i, codeCompletionGithub());
            case FIELD_RUNNER -> completionItemMap.put(i, codeCompletionRunner());
            case FIELD_INPUTS -> completionItemMap.put(i, listInputs(position));
            case FIELD_SECRETS -> completionItemMap.put(i, listSecrets(position));
            case FIELD_NEEDS -> completionItemMap.put(i, codeCompletionNeeds(position));
            default -> {
                // No Selection
                final boolean isOnOutput = getParent(position, FIELD_OUTPUTS).flatMap(outputs -> getParent(position, FIELD_ON)).isPresent();
                // SHOW ONLY JOBS [on.workflow_call.outputs.key.value:xxx]
                if (isOnOutput) {
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, FIELD_DEFAULT_MAP.get(FIELD_JOBS), ICON_JOB)));
                } else if (getParent(position, "runs-on").isEmpty() && getParent(position, "os").isEmpty()) {
                    // DEFAULT
                    addDefaultCodeCompletionItems(i, position, completionItemMap);
                }
            }
        }
    }

    private static void addDefaultCodeCompletionItems(final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        Optional.of(FIELD_DEFAULT_MAP)
                .map(map -> {
                    final Map<String, String> copyMap = new HashMap<>(map);
                    Optional.of(listInputs(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_INPUTS));
                    Optional.of(listSecrets(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_SECRETS));
                    Optional.of(listJobs(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_JOBS));
                    Optional.of(listSteps(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_STEPS));
                    Optional.of(listJobNeeds(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_NEEDS));
                    return copyMap;
                })
                .map(map -> completionItemsOf(map, ICON_NODE))
                .ifPresent(items -> completionItemMap.put(i, items));
    }

    private static String getDefaultPrefix(final CompletionParameters parameters) {
        final String wholeText = parameters.getOriginalFile().getText();
        final int caretOffset = parameters.getOffset();
        final int indexStart = getStartIndex(wholeText, caretOffset - 1);
        return wholeText.substring(indexStart, caretOffset);
    }

    private static void addLookupElements(final CompletionResultSet resultSet, final Map<String, String> map, final NodeIcon icon, final char suffix) {
        if (!map.isEmpty()) {
            resultSet.addAllElements(toLookupElements(map, icon, suffix));
        }
    }

    private static List<LookupElement> toLookupElements(final Map<String, String> map, final NodeIcon icon, final char suffix) {
        return map.entrySet().stream().map(item -> toLookupElement(icon, suffix, item.getKey(), item.getValue())).toList();
    }

    @NotNull
    private static List<LookupElement> toLookupItems(final List<SimpleElement> items) {
        return items.stream().map(SimpleElement::toLookupElement).toList();
    }
}
