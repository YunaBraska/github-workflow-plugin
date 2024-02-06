package com.github.yunabraska.githubworkflow;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLScalarListImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.GhwVariable.ghwVariablesOf;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_IF;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.PATTERN_GITHUB_ENV;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.PATTERN_GITHUB_OUTPUT;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.deleteInvalidAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newReloadAction;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.newUnresolvedAction;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getProject;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getProjectOrDefault;
import static com.github.yunabraska.githubworkflow.services.GitHubActionCache.getActionCache;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

public class Step extends WorkflowObject {

    final String id;
    final String name;
    final String condition;
    final String uses;
    final Map<String, With> definedInputs;
    final Map<String, Definition> definedOutputs = new LinkedHashMap<>();
    final Map<String, Definition> definedEnvs = new LinkedHashMap<>();
    final List<GhwVariable> variables = new ArrayList<>();

    public Step(final YAMLSequenceItem element) {
        super(element);
        id = PsiElementHelper.getText(element, "id").orElse(null);
        name = PsiElementHelper.getText(element, "name").orElse(null);
        uses = PsiElementHelper.getText(element, FIELD_USES).orElse(null);
        condition = PsiElementHelper.getText(element, FIELD_IF).orElse(null);
        definedInputs = PsiElementHelper.getChild(element, FIELD_WITH).map(PsiElementHelper::getKvChildren).map(children -> children.stream().map(With::new).collect(toMap(With::id, env -> env))).orElse(Collections.emptyMap());
        PsiElementHelper.getChild(element, FIELD_RUN).map(YAMLKeyValue::getValue).ifPresent(run -> {
            // Not performant but there is no way to access child nodes
            if (run instanceof final YAMLScalarListImpl list) {
                readDefinitions(list.getTextRange().getStartOffset(), list.getTextValue());
            } else {
                readDefinitions(run.getTextRange().getStartOffset(), run.getText());
            }
            // mark variables
            variables.addAll(ghwVariablesOf(run));
        });
    }

    protected void validate(final WorkflowFile workflow) {
        // TODO: download action
        element().ifPresent(element -> {
            ofNullable(uses).map(use -> getActionCache().get(getProjectOrDefault(element), use)).ifPresentOrElse(action -> {
                        action.getOutputs().forEach((key, value) -> definedOutputs.put(key, new Definition(element().orElse(null), key, value)));
                        if (action.isResolved() && !action.isLocal()) {
                            result.add(newReloadAction(action));
                        }
                        result.add(newSuppressAction(action));
                        highlightLocalActions(holder, element, action, result);
                        if (element != null && !action.isResolved() && (!action.isSuppressed())) {
                            result.add(action.isLocal() ? deleteInvalidAction(element) : newUnresolvedAction(element));
                        }
                    },
                    () -> result.add(newUnresolvedAction(element)));
    );

        });



        // TODO: mark definitions
        //make GHW_ANNOTATION_KEY
        //save GHW_ANNOTATION_KEY to run element
        Stream.of(definedOutputs, definedEnvs).map(Map::entrySet).flatMap(Collection::stream).map(definition -> new Annotation(definition.getKey(), NodeIcon.ICON_TEXT_VARIABLE)).toList();
        // TODO: run && references?
        variables.forEach(ghwVariable -> {
            final SimpleElement[] parts = ghwVariable.parts();

        });
        // only needed for internal processing
        variables.clear();
        // TODO: uses
        // TODO: envs
        // TODO: with
        // TODO: secrets
    }

    //  mark defined outputs
    //  mark defined envs
    //  mark validate variables in run field
    //  mark condition variables in run field

    private void readDefinitions(final int startOffset, final String text) {
        if (text.contains("GITHUB_OUTPUT")) {
            final Matcher matcher = PATTERN_GITHUB_OUTPUT.matcher(text);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    definedOutputs.put(matcher.group(1), new Definition(new TextRange(startOffset + matcher.start(), startOffset + matcher.end()), matcher.group(1), matcher.group(2)));
                }
            }
        } else if (text.contains("GITHUB_ENV")) {
            final Matcher matcher = PATTERN_GITHUB_ENV.matcher(text);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    definedEnvs.put(matcher.group(1), new Definition(new TextRange(startOffset + matcher.start(), startOffset + matcher.end()), matcher.group(1), matcher.group(2)));
                }
            }
        }
    }

    public String idOrNameOrUses() {
        return ofNullable(id).or(() -> ofNullable(name)).or(() -> ofNullable(uses)).orElseGet(() -> element().map(PsiElement::getText).orElse(null));
    }

    public String displayName() {
        return ofNullable(uses).or(() -> ofNullable(name)).or(() -> ofNullable(id)).orElseGet(() -> element().map(PsiElement::getText).orElse(null));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String condition() {
        return condition;
    }

    public String uses() {
        return uses;
    }

    public Map<String, With> with() {
        return definedInputs;
    }

    public Map<String, Definition> definedOutputs() {
        return definedOutputs;
    }

    public Map<String, Definition> definedEnvs() {
        return definedEnvs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Step step = (Step) o;
        return Objects.equals(idOrNameOrUses(), step.idOrNameOrUses());
    }

    @Override
    public int hashCode() {
        return Objects.hash(idOrNameOrUses());
    }

    @Override
    public String toString() {
        return "Step{" +
                "id='" + idOrNameOrUses() + '\'' +
                ", uses='" + uses + '\'' +
                ", with='" + definedInputs.size() + '\'' +
                ", out='" + definedOutputs.size() + '\'' +
                ", env='" + definedEnvs.size() + '\'' +
                ", conditional='" + (condition != null) + '\'' +
                '}';
    }
}
