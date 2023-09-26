package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
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
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NEEDS;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_RUNNER;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_STEP;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getStartIndex;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper.toLookupElement;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.*;
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
                    caretBracketItem.ifPresent(cbi -> {
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
                    });
                    //ACTIONS && WORKFLOWS
                    if (caretBracketItem.isEmpty()) {
                        if (getParent(position, FIELD_NEEDS).isPresent()) {
                            //[jobs.job_name.needs] list previous jobs
                            Optional.of(getJobNeeds(position)).filter(cil -> !cil.isEmpty())
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
            case FIELD_JOBS ->
                    completionItemMap.put(i, listJobOutputs(listJobs(position).stream().filter(job -> job.getKeyText().equals(cbi[1])).findFirst().orElse(null)));
            case FIELD_NEEDS ->
                    completionItemMap.put(i, listJobOutputs(listAllJobs(position).stream().filter(job -> job.getKeyText().equals(cbi[1])).findFirst().orElse(null)));
            case FIELD_STEPS ->
                    completionItemMap.put(i, listStepOutputs(listSteps(position).stream().filter(step -> getText(step, FIELD_ID).filter(id -> id.equals(cbi[1])).isPresent()).findFirst().orElse(null)));
            default -> {
                // ignored
            }
        }
    }

    private static void handleSecondItem(final String[] cbi, final int i, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_JOBS, FIELD_NEEDS, FIELD_STEPS ->
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_OUTPUTS, "", ICON_OUTPUT)));
            default -> {
                // ignored
            }
        }
    }

    public static List<SimpleElement> getJobs(final PsiElement psiElement) {
        return listJobs(psiElement).stream().map(job -> jobToCompletionItem(job, ICON_JOB)).toList();
    }

    public static List<SimpleElement> getSteps(final PsiElement psiElement) {
        return listSteps(psiElement).stream().map(item -> {
            final List<YAMLKeyValue> children = PsiElementHelper.getChildren(item);
            return children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).map(stepId -> completionItemOf(
                    stepId,
                    children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).orElse(null)),
                    ICON_STEP
            )).orElse(null);
        }).filter(Objects::nonNull).toList();
    }

    public static List<SimpleElement> getJobNeeds(final PsiElement psiElement) {
        final List<YAMLKeyValue> jobs = getParentJob(psiElement).map(job -> listAllJobs(psiElement).stream().takeWhile(j -> !j.getKeyText().equals(job.getKeyText())).toList()).orElseGet(Collections::emptyList);
        return listJobNeeds(psiElement).stream()
                .map(need -> jobs.stream().filter(job -> job.getKeyText().equals(need)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .map(need -> jobToCompletionItem(need, ICON_NEEDS))
                .toList();

    }

    @NotNull
    private static SimpleElement jobToCompletionItem(final YAMLKeyValue item, final NodeIcon nodeIcon) {
        final List<YAMLKeyValue> children = PsiElementHelper.getChildren(item);
        final YAMLKeyValue usesOrName = children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().orElse(null));
        return completionItemOf(
                children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(PsiElementHelper::getText).orElse(item.getKeyText()),
                ofNullable(usesOrName).flatMap(PsiElementHelper::getText).orElse(""),
                nodeIcon
        );
    }

    private static void handleFirstItem(final String[] cbi, final int i, final PsiElement position, final Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, getSteps(position));
            case FIELD_JOBS -> completionItemMap.put(i, getJobs(position));
            case FIELD_ENVS -> completionItemMap.put(i, listEnvs(position));
            case FIELD_GITHUB ->
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_GITHUB).get(), ICON_ENV));
            case FIELD_RUNNER ->
                    completionItemMap.put(i, completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_RUNNER).get(), ICON_RUNNER));
            case FIELD_INPUTS -> completionItemMap.put(i, listInputs(position));
            case FIELD_SECRETS -> completionItemMap.put(i, listSecrets(position));
            case FIELD_NEEDS -> completionItemMap.put(i, getJobNeeds(position));
            default -> {
                // No Selection
                final boolean isOnOutput = getParent(position, FIELD_OUTPUTS).flatMap(outputs -> getParent(position, FIELD_ON)).isPresent();
                // SHOW ONLY JOBS [on.workflow_call.outputs.key.value:xxx]
                if (isOnOutput) {
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                } else if (getParent(position, "runs-on").isEmpty() && getParent(position, "os").isEmpty()) {
                    //DEFAULT
                    ofNullable(DEFAULT_VALUE_MAP.getOrDefault(FIELD_DEFAULT, null))
                            .map(Supplier::get)
                            .map(map -> {
                                final Map<String, String> copyMap = new HashMap<>(map);
                                //'JOBS' HAS ONLY ONE PLACE
                                copyMap.remove(FIELD_JOBS);
                                //IF NO 'NEEDS' IS DEFINED
                                if (getParentJob(position).flatMap(job -> PsiElementHelper.getChild(job, FIELD_NEEDS)).isEmpty()) {
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
