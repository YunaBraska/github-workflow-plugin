package com.github.yunabraska.githubworkflow.logic;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_JOB;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SERVICES;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isField2Valid;
import static com.github.yunabraska.githubworkflow.helper.HighlightAnnotatorHelper.isValidItem3;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getTextElements;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.removeQuotes;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;

public class JobContext {

    private static final String FIELD_CONTAINER = "container";
    private static final String FIELD_ID = "id";
    private static final String FIELD_NETWORK = "network";
    private static final String FIELD_PORTS = "ports";
    private static final Pattern PORT_PATTERN = Pattern.compile("(\\d+)(?:/(?:tcp|udp))?(?::\\d+)?");
    private static final List<String> CONTAINER_FIELDS = List.of(FIELD_ID, FIELD_NETWORK);
    private static final List<String> SERVICE_FIELDS = List.of(FIELD_ID, FIELD_NETWORK, FIELD_PORTS);

    public static void highlightJob(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, field -> {
            if (!isDefinedItem0(element, holder, field, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_JOB).get().keySet()))) {
                return;
            }
            switch (field.text()) {
                case FIELD_CONTAINER -> highlightContainer(holder, element, parts);
                case FIELD_SERVICES -> highlightServices(holder, element, parts);
                default -> ifEnoughItems(holder, element, parts, 2, 2, ignored -> {
                    // Valid scalar job context field.
                });
            }
        });
    }

    public static List<SimpleElement> codeCompletionJob() {
        return completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_JOB).get(), ICON_JOB);
    }

    public static List<SimpleElement> codeCompletionJob(final String parent, final PsiElement position) {
        return switch (parent) {
            case FIELD_CONTAINER -> completionItemsOf(toMap(CONTAINER_FIELDS, "Job container field"), ICON_NODE);
            case FIELD_SERVICES -> completionItemsOf(toMap(listServiceIds(position), "Job service"), ICON_NODE);
            default -> List.of();
        };
    }

    public static List<SimpleElement> codeCompletionJob(final String parent, final String child, final PsiElement position) {
        if (FIELD_SERVICES.equals(parent) && listServiceIds(position).contains(child)) {
            return completionItemsOf(toMap(SERVICE_FIELDS, "Job service field"), ICON_NODE);
        }
        return List.of();
    }

    public static List<SimpleElement> codeCompletionJob(final String parent, final String serviceId, final String serviceField, final PsiElement position) {
        if (FIELD_SERVICES.equals(parent) && FIELD_PORTS.equals(serviceField)) {
            return completionItemsOf(toMap(listServicePorts(position, serviceId), "Mapped service port"), ICON_NODE);
        }
        return List.of();
    }

    public static List<String> listServiceIds(final PsiElement psiElement) {
        return getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_SERVICES))
                .map(services -> com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChildren(services).stream()
                        .map(YAMLKeyValue::getKeyText)
                        .toList())
                .orElseGet(List::of);
    }

    public static Optional<YAMLKeyValue> getService(final PsiElement psiElement, final String serviceId) {
        return getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_SERVICES))
                .flatMap(services -> com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChildren(services).stream()
                        .filter(service -> serviceId.equals(service.getKeyText()))
                        .findFirst());
    }

    public static List<String> listServicePorts(final PsiElement psiElement, final String serviceId) {
        return getService(psiElement, serviceId)
                .flatMap(service -> getChild(service, FIELD_PORTS))
                .map(ports -> getTextElements(ports).stream()
                        .map(PsiElement::getText)
                        .map(JobContext::toServicePort)
                        .flatMap(Optional::stream)
                        .distinct()
                        .toList())
                .orElseGet(List::of);
    }

    private static void highlightContainer(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        if (parts.length > 2 && isField2Valid(element, holder, parts[2], CONTAINER_FIELDS)) {
            ifEnoughItems(holder, element, parts, 2, 3, ignored -> {
                // Valid container object/member.
            });
        }
    }

    private static void highlightServices(final AnnotationHolder holder, final LeafPsiElement element, final SimpleElement[] parts) {
        if (parts.length < 3) {
            return;
        }
        if (!isDefinedItem0(element, holder, parts[2], listServiceIds(element))) {
            return;
        }
        if (parts.length == 3) {
            return;
        }
        if (!isField2Valid(element, holder, parts[3], SERVICE_FIELDS)) {
            return;
        }
        if (!FIELD_PORTS.equals(parts[3].text())) {
            ifEnoughItems(holder, element, parts, 2, 4, ignored -> {
                // Valid service scalar member.
            });
            return;
        }
        if (parts.length > 4) {
            isValidItem3(element, holder, parts[4], listServicePorts(element, parts[2].text()));
        }
        ifEnoughItems(holder, element, parts, 2, 5, ignored -> {
            // Valid service port access.
        });
    }

    private static java.util.Map<String, String> toMap(final List<String> keys, final String description) {
        return keys.stream().collect(java.util.stream.Collectors.toMap(
                key -> key,
                key -> description,
                (existing, replacement) -> existing,
                java.util.LinkedHashMap::new
        ));
    }

    private static Optional<String> toServicePort(final String text) {
        final Matcher matcher = PORT_PATTERN.matcher(removeQuotes(text));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private JobContext() {
        // static helper class
    }
}
