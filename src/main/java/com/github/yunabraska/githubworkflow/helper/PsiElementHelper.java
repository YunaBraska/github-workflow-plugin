package com.github.yunabraska.githubworkflow.helper;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.services.GitHubActionCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.*;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_ROOT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_STEP;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_INPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_SECRET_WORKFLOW;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

public class PsiElementHelper {

    private PsiElementHelper() {
        // static helper class
    }

    public static List<SimpleElement> listJobOutputs(final YAMLKeyValue job) {
        //JOB OUTPUTS
        final List<SimpleElement> jobOutputs = ofNullable(job)
                .flatMap(j -> getChild(j, FIELD_OUTPUTS)
                        .map(PsiElementHelper::getChildren)
                        .map(children -> children.stream().map(child -> getText(child).map(value -> completionItemOf(child.getKeyText(), value, ICON_OUTPUT)).orElse(null)).filter(Objects::nonNull).toList())
                ).orElseGet(Collections::emptyList);

        //JOB USES OUTPUTS
        return Stream.concat(jobOutputs.stream(), getUsesOutputs(job).stream()).toList();
    }

    public static List<SimpleElement> listStepOutputs(final YAMLSequenceItem step) {
        //STEP RUN OUTPUTS
        final List<SimpleElement> stepOutputs = ofNullable(step).flatMap(s -> getChild(s, FIELD_RUN)
                .map(PsiElementHelper::parseOutputVariables)
                .map(outputs -> outputs.stream().map(output -> completionItemOf(output.key(), output.text(), ICON_TEXT_VARIABLE)).toList())
        ).orElseGet(Collections::emptyList);

        //STEP USES OUTPUTS
        return Stream.concat(stepOutputs.stream(), getUsesOutputs(step).stream()).toList();
    }

    public static List<YAMLKeyValue> listJobs(final PsiElement psiElement) {
        //JobList is only valid in Workflow outputs
        return getParent(psiElement, FIELD_OUTPUTS)
                .flatMap(outputs -> getParent(psiElement, FIELD_ON))
                .map(PsiElementHelper::listAllJobs)
                .orElseGet(Collections::emptyList);
    }

    public static List<YAMLKeyValue> listAllJobs(final PsiElement psiElement) {
        return ofNullable(psiElement).map(element -> getAllElements(element.getContainingFile(), FIELD_JOBS).stream().flatMap(jobs -> getChildren(jobs, YAMLKeyValue.class).stream()).toList()).orElseGet(Collections::emptyList);
    }

    public static List<String> listJobNeeds(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .flatMap(PsiElementHelper::getParentJob)
                .flatMap(job -> getChild(job, FIELD_NEEDS))
                .map(needs -> getTextElements(needs)
                        .stream().map(PsiElement::getText)
                        .map(PsiElementHelper::removeQuotes)
                        .filter(PsiElementHelper::hasText)
                        .toList()
                ).orElseGet(Collections::emptyList);
    }

    public static List<YAMLSequenceItem> listSteps(final PsiElement psiElement) {
        // StepList position == step?    list previous steps in current job
        // StepList position == outputs? list all      steps in current job
        return getParentJob(psiElement).map(job -> {
            final YAMLSequenceItem currentStep = getParentStep(psiElement).orElse(null);
            final boolean isOutput = getParent(psiElement, FIELD_OUTPUTS).isPresent();
            return getChildSteps(job).stream().takeWhile(step -> isOutput || step != currentStep).toList();
        }).orElseGet(() -> getParent(psiElement, FIELD_OUTPUTS)
                //Action.yaml [runs.steps]
                .map(outputs -> psiElement.getContainingFile())
                .flatMap(psiFile -> PsiElementHelper.getChild(psiFile, FIELD_RUNS))
                .flatMap(runs -> PsiElementHelper.getChild(runs, FIELD_STEPS))
                .map(PsiElementHelper::getChildSteps)
                .orElseGet(Collections::emptyList)
        );
    }

    public static Optional<YAMLKeyValue> getParentJob(final PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_JOBS, YAMLKeyValue.class);
    }

    public static List<SimpleElement> listEnvs(final PsiElement psiElement) {
        // CURRENT STEP TEXT ENVS [jobs.job_id.steps.step_id.run:key=value]
        final TextRange currentRange = psiElement.getTextRange();
        final List<SimpleElement> result = new ArrayList<>(completionItemsOf(
                getAllElements(psiElement.getContainingFile(), FIELD_RUN).stream()
                        // only FIELD_RUN from previous FIELD_STEP
                        .filter(keyValue -> getParentStep(keyValue).map(PsiElement::getTextRange).map(TextRange::getStartOffset).orElse(currentRange.getEndOffset()) < currentRange.getStartOffset())
                        .map(PsiElementHelper::parseEnvVariables)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toMap(SimpleElement::key, SimpleElement::textNoQuotes, (existing, replacement) -> existing))
                , ICON_TEXT_VARIABLE
        ));

        // CURRENT STEP ENVS [step.env.env_key:env_value]
        getParentStep(psiElement)
                .flatMap(step -> getChild(step, FIELD_ENVS))
                .map(PsiElementHelper::getChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_STEP))
                .ifPresent(result::addAll);

        // CURRENT JOB ENVS [jobs.job_id.envs.env_id:env_value]
        getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_ENVS))
                .map(PsiElementHelper::getChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_JOB))
                .ifPresent(result::addAll);


        // WORKFLOW ENVS
        getChild(psiElement.getContainingFile(), FIELD_ENVS)
                .map(PsiElementHelper::getChildren)
                .map(toMapWithKeyAndText())
                .map(map -> completionItemsOf(map, ICON_ENV_ROOT))
                .ifPresent(result::addAll);

        //DEFAULT ENVS
        result.addAll(completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_ENVS).get(), ICON_ENV));

        return result;
    }

    public static List<SimpleElement> listSecrets(final PsiElement psiElement) {
        //WORKFLOW SECRETS
        return getChild(psiElement.getContainingFile(), FIELD_ON)
                .map(on -> getAllElements(on, FIELD_SECRETS))
                .map(secrets -> secrets.stream().flatMap(secret -> getChildren(secret).stream()).collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue, "description").orElse(""), (existing, replacement) -> existing)))
                .map(map -> completionItemsOf(map, ICON_SECRET_WORKFLOW))
                .orElseGet(ArrayList::new);
    }


    public static List<SimpleElement> listInputs(final PsiElement psiElement) {
        final Map<String, String> result = new HashMap<>();
        getAllElements(psiElement.getContainingFile(), FIELD_INPUTS).stream()
                .map(PsiElementHelper::getChildren)
                .flatMap(Collection::stream)
                .forEach(input -> {
                    final String description = getText(psiElement, "description").orElse("");
                    final String previousDescription = result.computeIfAbsent(input.getKeyText(), value -> description);
                    if (previousDescription.length() < description.length()) {
                        result.put(input.getKeyText(), description);
                    }
                });
        return completionItemsOf(result, ICON_INPUT);
    }

    public static List<SimpleElement> parseEnvVariables(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, PsiElementHelper::toGithubEnvs);
    }

    public static List<SimpleElement> parseOutputVariables(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, PsiElementHelper::toGithubOutputs);
    }

    public static <T> Optional<T> getChild(final PsiElement psiElement, final Class<T> clazz) {
        return getFirstElement(getChildren(psiElement, clazz));
    }

    public static <T> Optional<T> getFirstElement(final List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public static List<YAMLKeyValue> getAllJobs(final PsiElement psiElement) {
        final List<YAMLKeyValue> result = new ArrayList<>();
        getAllJobs(result, ofNullable(psiElement).map(PsiElement::getContainingFile).orElse(null));
        return unmodifiableList(result);
    }

    public static void getAllJobs(final List<YAMLKeyValue> result, final PsiElement element) {
        if (result == null || element == null) {
            return;
        }
        if (element instanceof final YAMLKeyValue keyValue && FIELD_JOBS.equals(keyValue.getKeyText())) {
            result.addAll(getChildren(keyValue));
        } else {
            Arrays.stream(element.getChildren()).forEach(child -> getAllJobs(result, child));
        }
    }

    public static List<YAMLKeyValue> getChildren(final PsiElement psiElement) {
        return getChildren(psiElement, YAMLKeyValue.class);
    }

    public static Optional<String> getText(final PsiElement psiElement) {
        return getTextElements(psiElement).stream().map(PsiElement::getText).map(PsiElementHelper::removeQuotes).filter(PsiElementHelper::hasText).findFirst();
    }

    public static Optional<String> getText(final PsiElement psiElement, final String key) {
        return getChild(psiElement, key).flatMap(PsiElementHelper::getText);
    }


    public static Optional<PsiElement> getTextElement(final PsiElement psiElement) {
        final List<PsiElement> textValues = getTextElements(psiElement);
        return textValues.isEmpty() ? Optional.empty() : Optional.of(textValues.get(0));
    }

    public static List<PsiElement> getTextElements(final PsiElement psiElement) {
        final ArrayList<PsiElement> result = new ArrayList<>();
        getTextElements(result, psiElement);
        return unmodifiableList(result);
    }

    public static void getTextElements(final List<PsiElement> result, final PsiElement psiElement) {
        ofNullable(psiElement).ifPresent(element -> {
            if (isTextElement(element)) {
                if (hasText(element.getText())) {
                    result.add(element);
                }
            } else {
                Arrays.stream(element.getChildren()).forEach(child -> getTextElements(result, child));
            }
        });
    }

    public static boolean isTextElement(final PsiElement element) {
        return element instanceof YAMLPlainTextImpl || element instanceof YAMLQuotedText;
    }

    public static List<YAMLKeyValue> getAllElements(final PsiElement psiElement, final String keyName) {
        return psiElement == null || keyName == null ? Collections.emptyList() : unmodifiableList(getAllElements(new ArrayList<>(), psiElement, keyName));
    }

    public static List<YAMLKeyValue> getAllElements(final List<YAMLKeyValue> result, final PsiElement psiElement, final String keyName) {
        if (psiElement instanceof final YAMLKeyValue keyValue && keyName.equals(keyValue.getKeyText())) {
            result.add(keyValue);
        }

        for (final PsiElement child : psiElement.getChildren()) {
            getAllElements(result, child, keyName);
        }

        return result;
    }

    public static Optional<PsiElement> getParentStepOrJob(final PsiElement psiElement) {
        return getParentStep(psiElement).map(PsiElement.class::cast).or(() -> getParentJob(psiElement));
    }

    public static Optional<YAMLSequenceItem> getParentStep(final PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_STEPS, YAMLSequenceItem.class);
    }

    //TOTO: getChild(psiElement, FIELD_STEPS)???
    public static List<YAMLSequenceItem> getChildSteps(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .map(element -> element instanceof final YAMLKeyValue keyValue && FIELD_STEPS.equals(keyValue.getKeyText()) ? List.of(keyValue) : getAllElements(element, FIELD_STEPS))
                .map(yamlKeyValues -> yamlKeyValues.stream().flatMap(steps -> getChildren(steps, YAMLSequenceItem.class).stream().filter(Objects::nonNull)).toList())
                .orElseGet(Collections::emptyList);
    }

    public static <T> List<T> getChildren(final PsiElement psiElement, final Class<T> clazz) {
        return ofNullable(psiElement)
                .map(PsiElement::getChildren)
                .map(psiElements -> Arrays.stream(psiElements).filter(clazz::isInstance).map(clazz::cast).toList())
                .filter(children -> !children.isEmpty())
                .or(() -> ofNullable(psiElement)
                        .map(PsiElement::getChildren)
                        .flatMap(psiElements -> Arrays.stream(psiElements).map(child -> getChildren(child, clazz)).filter(children -> !children.isEmpty()).findFirst())
                )
                .orElseGet(Collections::emptyList);
    }

    public static Optional<YAMLKeyValue> getChild(final PsiElement psiElement, final String childKey) {
        return psiElement == null || childKey == null ? Optional.empty() : Optional.of(psiElement)
                .map(PsiElementHelper::getChildren)
                .flatMap(children -> children.stream()
                        .filter(Objects::nonNull)
                        .filter(child -> childKey.equals(child.getKeyText()))
                        .findFirst()
                        .or(() -> children.stream()
                                .filter(Objects::nonNull)
                                .map(child -> getChild(child, childKey))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .findFirst())
                );
    }

    public static <T extends PsiElement> Optional<T> getElementUnderParent(final PsiElement psiElement, final String keyName, final Class<T> clazz) {
        return psiElement == null || keyName == null ? Optional.empty() : getParent(psiElement, yamlKeyValue -> keyName.equals(yamlKeyValue.getKeyText()))
                .flatMap(yamlKeyValue -> getClosestChild(psiElement, yamlKeyValue, clazz));
    }

    public static Optional<YAMLKeyValue> getParent(final PsiElement psiElement, final String fieldKey) {
        return psiElement == null || fieldKey == null ? Optional.empty() : getParent(psiElement, parent -> fieldKey.equals(parent.getKeyText()));
    }

    public static Optional<YAMLKeyValue> getParent(final PsiElement psiElement, final Predicate<YAMLKeyValue> filter) {
        return psiElement == null || filter == null ? Optional.empty() : Optional.of(psiElement)
                .flatMap(PsiElementHelper::toYAMLKeyValue)
                .filter(filter)
                .or(() -> Optional.of(psiElement)
                        .map(PsiElement::getParent)
                        .flatMap(parent -> getParent(parent, filter))
                );
    }

    public static Optional<YAMLKeyValue> toYAMLKeyValue(final PsiElement psiElement) {
        if (psiElement instanceof final YAMLKeyValue element) {
            return Optional.of(element);
        }
        return Optional.empty();
    }

    public static String getDescription(final PsiElement psiElement) {
        return psiElement == null ? "" : "r[" + getText(psiElement, "required").map(Boolean::parseBoolean).orElse(false) + "]"
                + getText(psiElement, "default").map(def -> " def[" + def + "]").orElse("")
                + getText(psiElement, "description").or(() -> getText(psiElement, "desc")).map(desc -> " " + desc).orElse("");
    }

    public static Project getProject(final PsiElement psiElement) {
        return psiElement != null && psiElement.isValid() ? psiElement.getProject() : null;
    }

    public static String removeQuotes(final String result) {
        return removeBrackets(result, '"', '\'');
    }

    public static boolean hasText(final String str) {
        return (str != null && !str.isEmpty() && containsText(str));
    }

    private static Map<String, String> toGithubOutputs(final String text) {
        final Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_OUTPUT")) {
            final Matcher matcher = PATTERN_GITHUB_OUTPUT.matcher(text);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    variables.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return variables;
    }

    private static Map<String, String> toGithubEnvs(final String text) {
        final Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_ENV")) {
            final Matcher matcher = PATTERN_GITHUB_ENV.matcher(text);
            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    variables.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return variables;
    }

    private static String removeBrackets(final String text, final char... chars) {
        if (text != null && text.length() > 1) {
            for (final char c : chars) {
                if (text.charAt(0) == c && text.charAt(text.length() - 1) == (c == '[' ? ']' : validateRoundBracket(c))) {
                    return text.substring(1, text.length() - 1);
                }
            }
        }
        return text;
    }

    private static List<SimpleElement> getUsesOutputs(final PsiElement psiElement) {
        return ofNullable(psiElement)
                .flatMap(element -> getChild(element, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .map(GitHubAction::freshOutputs)
                .map(map -> completionItemsOf(map, ICON_OUTPUT))
                .orElseGet(Collections::emptyList);
    }

    private static Function<List<YAMLKeyValue>, Map<String, String>> toMapWithKeyAndText() {
        return elements -> elements.stream()
                .filter(keyValue -> getTextElement(keyValue).isPresent())
                .collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue).orElse(""), (existing, replacement) -> existing));
    }

    private static <T extends PsiElement> Optional<T> getClosestChild(final PsiElement from, final YAMLKeyValue to, final Class<T> clazz) {
        return listAllParents(from, to).stream()
                .filter(Objects::nonNull)
                .filter(parent -> !(parent instanceof YAMLBlockSequenceImpl))
                .filter(clazz::isInstance)
                .findFirst()
                .map(clazz::cast);
    }

    private static List<PsiElement> listAllParents(final PsiElement from, final PsiElement to) {
        final List<PsiElement> result = new ArrayList<>();
        listAllParents(result, from.getParent(), to);
        Collections.reverse(result);
        return result;
    }

    private static void listAllParents(final List<PsiElement> result, final PsiElement from, final PsiElement to) {
        if (from != null && from != to) {
            result.add(from);
            listAllParents(result, from.getParent(), to);
        }
    }

    private static List<SimpleElement> parseVariables(final PsiElement psiElement, final Function<String, Map<String, String>> method) {
        final List<SimpleElement> lineElements = getLineElements(psiElement);
        return lineElements.stream().flatMap(line -> method.apply(line.text()).entrySet().stream().map(env -> new SimpleElement(env.getKey(), env.getValue(), line.range()))).toList();
    }

    private static List<SimpleElement> getLineElements(final PsiElement psiElement) {
        return getChild(psiElement, YAMLBlockScalarImpl.class).map(psi -> {
            final TextRange parentRange = psi.getTextRange();
            return psi.getContentRanges().stream().map(textRange -> new SimpleElement(
                    null,
                    removeQuotes(psi.getText().substring(textRange.getStartOffset(), textRange.getEndOffset())),
                    new TextRange(parentRange.getStartOffset() + textRange.getStartOffset(), parentRange.getStartOffset() + textRange.getEndOffset())
            )).filter(element -> element.startIndexOffset() < element.endIndexOffset()).filter(element -> hasText(element.text())).toList();
        }).orElseGet(Collections::emptyList);
    }

    private static boolean containsText(final CharSequence str) {
        final int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static char validateRoundBracket(final char c) {
        return c == '(' ? ')' : c;
    }
}
