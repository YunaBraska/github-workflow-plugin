package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.syntax.WorkflowReferences;

import com.github.yunabraska.githubworkflow.state.GitHubActionCache;

import com.github.yunabraska.githubworkflow.i18n.GitHubWorkflowBundle;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.syntax.Steps.listStepOutputs;
import static java.util.Optional.ofNullable;

public final class WorkflowDocumentationProvider extends AbstractDocumentationProvider {

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(
            final Editor editor,
            final PsiFile file,
            final PsiElement contextElement,
            final int targetOffset
    ) {
        if (contextElement == null || targetOffset < 0 || targetOffset > file.getTextLength()) {
            return null;
        }
        return documentationCandidates(file, contextElement, targetOffset).stream()
                .flatMap(candidate -> documentationAt(candidate, targetOffset).stream())
                .findFirst()
                .map(payload -> new WorkflowDocumentationElement(contextElement, payload))
                .orElse(null);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(final PsiElement element, final PsiElement originalElement) {
        return element instanceof WorkflowDocumentationElement workflowElement
                ? workflowElement.payload().hint()
                : null;
    }

    @Override
    public @Nullable String generateHoverDoc(final PsiElement element, final PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    @Override
    public @Nullable String generateDoc(final PsiElement element, final PsiElement originalElement) {
        return element instanceof WorkflowDocumentationElement workflowElement
                ? workflowElement.payload().html()
                : null;
    }

    static Optional<DocPayload> documentationAt(final PsiElement element, final int absoluteOffset) {
        return declaredOutputDoc(element)
                .or(() -> actionParameterDoc(element))
                .or(() -> textElement(element).flatMap(WorkflowDocumentationProvider::actionUseDoc))
                .or(() -> textElement(element).flatMap(text -> variableDoc(text, absoluteOffset)));
    }

    private static List<PsiElement> documentationCandidates(final PsiFile file, final PsiElement contextElement, final int targetOffset) {
        final Set<PsiElement> result = new LinkedHashSet<>();
        addCandidate(result, contextElement);
        addCandidate(result, file.findElementAt(Math.min(targetOffset, Math.max(0, file.getTextLength() - 1))));
        if (targetOffset > 0) {
            addCandidate(result, file.findElementAt(targetOffset - 1));
        }
        if (targetOffset + 1 < file.getTextLength()) {
            addCandidate(result, file.findElementAt(targetOffset + 1));
        }
        return List.copyOf(result);
    }

    private static void addCandidate(final Set<PsiElement> result, final PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getParent() != current) {
            result.add(current);
            if (current instanceof YAMLKeyValue) {
                return;
            }
            current = current.getParent();
        }
    }

    private static Optional<DocPayload> actionUseDoc(final PsiElement textElement) {
        return getParent(textElement, FIELD_USES)
                .map(GitHubActionCache::getAction)
                .map(WorkflowDocumentationProvider::actionDoc);
    }

    private static Optional<DocPayload> actionParameterDoc(final PsiElement element) {
        return keyValueAt(element)
                .filter(item -> getParent(item.getParent(), FIELD_WITH).isPresent()
                        || getParent(item.getParent(), FIELD_SECRETS).isPresent())
                .flatMap(item -> getParentStepOrJob(item)
                        .map(GitHubActionCache::getAction)
                        .flatMap(action -> parameterDoc(item, action)));
    }

    private static Optional<DocPayload> parameterDoc(final YAMLKeyValue item, final GitHubAction action) {
        if (action == null || !action.isResolved()) {
            return Optional.empty();
        }
        final boolean secret = getParent(item.getParent(), FIELD_SECRETS).isPresent();
        final String name = item.getKeyText();
        final String details = secret ? action.freshSecrets().get(name) : action.freshInputs().get(name);
        final String label = secret ? message("documentation.secret.label") : message("documentation.input.label");
        return ofNullable(details)
                .map(text -> new DocPayload(
                        label + " " + name,
                        renderParameter(label, name, text, action.githubUrl()),
                        plainParameter(label, name, text)
                ));
    }

    private static Optional<DocPayload> variableDoc(final PsiElement textElement, final int absoluteOffset) {
        final int offsetInElement = absoluteOffset - textElement.getTextRange().getStartOffset();
        final Optional<WorkflowReferences.Target> target = WorkflowReferences.resolveAt(textElement, offsetInElement).stream().findFirst();
        if (target.isPresent()) {
            return Optional.of(referenceDoc(target.get()));
        }
        return WorkflowReferences.segmentAt(textElement, offsetInElement)
                .flatMap(WorkflowDocumentationProvider::contextDoc);
    }

    private static DocPayload referenceDoc(final WorkflowReferences.Target target) {
        return switch (target.kind()) {
            case "input" -> yamlParameterDoc(message("documentation.input.label"), true, target.segment().text(), target.target());
            case "secret" -> yamlParameterDoc(message("documentation.secret.label"), false, target.segment().text(), target.target());
            case "env" -> yamlValueDoc(message("documentation.env.label"), target.segment().text(), target.target());
            case "matrix" -> yamlValueDoc(message("documentation.matrix.label"), target.segment().text(), target.target());
            case "step" -> stepDoc(target.target());
            case "step-output" -> stepOutputDoc(target.segment().text(), target.target());
            case "need" -> simpleDoc(message("documentation.need.label"), target.segment().text(), message("documentation.need.description"));
            case "need-output" -> outputDoc(message("documentation.needOutput.label"), target.segment().text(), target.target());
            case "job" -> simpleDoc(message("documentation.reusableJob.label"), target.segment().text(), message("documentation.reusableJob.description"));
            case "job-output" -> outputDoc(message("documentation.reusableJobOutput.label"), target.segment().text(), target.target());
            case "service" -> yamlValueDoc(message("documentation.service.label"), target.segment().text(), target.target());
            case "service-port" -> yamlValueDoc(message("documentation.servicePort.label"), target.segment().text(), target.target());
            case "container" -> yamlValueDoc(message("documentation.container.label"), target.segment().text(), target.target());
            default -> simpleDoc(message("documentation.symbol.label"), target.segment().text(), message("documentation.symbol.description"));
        };
    }

    private static Optional<DocPayload> declaredOutputDoc(final PsiElement element) {
        return keyValueAt(element)
                .filter(output -> getParent(output.getParent(), FIELD_OUTPUTS).isPresent())
                .filter(WorkflowDocumentationProvider::isDirectOutput)
                .filter(output -> getParent(output, FIELD_WITH).isEmpty())
                .map(output -> outputDoc(outputLabel(output), output.getKeyText(), output));
    }

    private static boolean isDirectOutput(final YAMLKeyValue output) {
        return output.getParent() != null
                && output.getParent().getParent() instanceof YAMLKeyValue parent
                && FIELD_OUTPUTS.equals(parent.getKeyText());
    }

    private static String outputLabel(final YAMLKeyValue output) {
        return getParent(output, FIELD_ON).isPresent()
                ? message("documentation.workflowOutput.label")
                : message("documentation.jobOutput.label");
    }

    private static DocPayload actionDoc(final GitHubAction action) {
        final String title = action.displayName();
        final StringBuilder html = new StringBuilder();
        html.append("<h3>").append(escape(title)).append("</h3>");
        html.append("<p><b>").append(action.isAction()
                ? message("documentation.action.label")
                : message("documentation.reusableWorkflow.label")).append("</b>");
        if (action.isResolved()) {
            html.append(" ").append(message("documentation.resolvedFrom", "<code>" + escape(action.usesValue()) + "</code>"));
        } else {
            html.append(" ").append(message("documentation.notResolved"));
        }
        html.append("</p>");
        appendParagraph(html, action.description());
        appendLink(html, action.githubUrl());
        appendMap(html, message("documentation.inputs.title"), action.freshInputs());
        appendMap(html, message("documentation.outputs.title"), action.freshOutputs());
        appendMap(html, message("documentation.secrets.title"), action.freshSecrets());
        return new DocPayload(title, html.toString(), title + "\n" + action.usesValue());
    }

    private static DocPayload yamlParameterDoc(final String label, final boolean input, final String name, final PsiElement target) {
        final String details = target instanceof YAMLKeyValue keyValue
                ? WorkflowPsi.getDescription(keyValue, input)
                : "";
        return new DocPayload(
                label + " " + name,
                renderParameter(label, name, details, ""),
                plainParameter(label, name, details)
        );
    }

    private static DocPayload yamlValueDoc(final String label, final String name, final PsiElement target) {
        final String value = target instanceof YAMLKeyValue keyValue ? getText(keyValue).orElse("") : target.getText();
        final String html = "<h3>" + escape(label) + " <code>" + escape(name) + "</code></h3>"
                + (value.isBlank() ? "" : "<p><b>" + escape(message("documentation.value.label")) + ":</b> <code>" + escape(value) + "</code></p>");
        return new DocPayload(label + " " + name, html, label + " " + name);
    }

    private static DocPayload stepDoc(final PsiElement target) {
        final YAMLKeyValue id = target instanceof YAMLKeyValue keyValue ? keyValue : null;
        final Optional<org.jetbrains.yaml.psi.YAMLSequenceItem> stepItem = WorkflowPsi.getParentStep(target);
        final String name = id == null ? target.getText() : getText(id).orElse(id.getKeyText());
        final String title = message("documentation.step.title", name);
        final StringBuilder html = new StringBuilder("<h3>").append(escape(title)).append("</h3>");
        stepItem.flatMap(step -> getChild(step, "name")).flatMap(WorkflowPsi::getText).ifPresent(value -> appendDetail(html, message("documentation.name.label"), value));
        stepItem.flatMap(step -> getChild(step, FIELD_USES)).flatMap(WorkflowPsi::getText).ifPresent(value -> appendDetail(html, message("documentation.uses.label"), value));
        stepItem.flatMap(step -> getChild(step, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> action != null && action.isResolved())
                .ifPresent(action -> {
                    appendDetail(html, action.isAction()
                            ? message("documentation.action.label")
                            : message("documentation.reusableWorkflow.label"), action.displayName());
                    appendParagraph(html, action.description());
                });
        stepItem.flatMap(step -> getChild(step, "run")).flatMap(WorkflowPsi::getText).ifPresent(value -> appendDetail(html, message("documentation.run.label"), value));
        stepItem.ifPresent(step -> appendList(html, message("documentation.outputs.title"), listStepOutputs(step).stream().map(output -> output.key()).toList()));
        return new DocPayload(title, html.toString(), title);
    }

    private static DocPayload stepOutputDoc(final String outputName, final PsiElement target) {
        final Optional<StepOutputSource> source = stepOutputSource(target);
        final StringBuilder details = new StringBuilder();
        source.flatMap(value -> value.outputDescription(outputName)).ifPresent(value -> appendLine(details, message("documentation.description.label"), value));
        source.ifPresent(value -> {
            appendLine(details, message("documentation.step.label"), value.stepLabel());
            appendLine(details, message("documentation.uses.label"), value.usesValue());
            appendLine(details, message("documentation.source.label"), value.sourceLabel());
        });
        final String plainDetails = details.toString();
        final String label = message("documentation.stepOutput.label");
        final StringBuilder html = new StringBuilder(renderParameter(label, outputName, plainDetails, ""));
        source.flatMap(StepOutputSource::url).ifPresent(url -> appendLink(html, url, source.map(StepOutputSource::usesValue).orElse(url)));
        return new DocPayload(
                label + " " + outputName,
                html.toString(),
                plainParameter(label, outputName, plainDetails)
        );
    }

    private static Optional<StepOutputSource> stepOutputSource(final PsiElement target) {
        final Optional<org.jetbrains.yaml.psi.YAMLSequenceItem> stepItem = WorkflowPsi.getParentStep(target);
        return stepItem.map(step -> {
            final String stepId = getText(step, "id").orElse("");
            final String stepName = getText(step, "name").orElse("");
            final Optional<YAMLKeyValue> uses = getChild(step, FIELD_USES);
            final GitHubAction action = uses.map(GitHubActionCache::getAction)
                    .filter(candidate -> candidate != null && candidate.isResolved())
                    .orElse(null);
            return new StepOutputSource(stepId, stepName, uses.flatMap(WorkflowPsi::getText).orElse(""), action);
        });
    }

    private static DocPayload outputDoc(final String label, final String name, final PsiElement target) {
        final StringBuilder details = new StringBuilder();
        keyValueAt(target).ifPresent(output -> {
            appendLine(details, message("documentation.description.label"), getText(output, "description").orElse(""));
            appendLine(details, message("documentation.value.label"), getText(output, "value").or(() -> getText(output)).orElse(""));
            outputSourceDetails(output).ifPresent(source -> appendLine(details, message("documentation.source.label"), source));
        });
        return new DocPayload(
                label + " " + name,
                renderParameter(label, name, details.toString(), ""),
                plainParameter(label, name, details.toString())
        );
    }

    private static Optional<String> outputSourceDetails(final YAMLKeyValue output) {
        return WorkflowPsi.getTextElement(output)
                .stream()
                .flatMap(text -> WorkflowReferences.resolve(text).stream())
                .filter(target -> "step-output".equals(target.kind()))
                .findFirst()
                .map(target -> target.target() instanceof YAMLKeyValue uses && FIELD_USES.equals(uses.getKeyText())
                        ? GitHubActionCache.getAction(uses)
                        : null)
                .filter(action -> action != null && action.isResolved())
                .map(action -> action.displayName() + (action.description().isBlank() ? "" : " - " + action.description()));
    }

    private static void appendLine(final StringBuilder builder, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(name).append(": ").append(value);
        }
    }

    private static Optional<DocPayload> contextDoc(final com.github.yunabraska.githubworkflow.model.SimpleElement segment) {
        final String text = segment.text();
        return switch (text) {
            case "github" -> Optional.of(simpleDoc(message("documentation.context.github"), "github", message("documentation.context.github.description")));
            case "gitea" -> Optional.of(simpleDoc(message("documentation.context.gitea"), "gitea", message("documentation.context.gitea.description")));
            case "inputs" -> Optional.of(simpleDoc(message("documentation.context.inputs"), "inputs", message("documentation.context.inputs.description")));
            case "secrets" -> Optional.of(simpleDoc(message("documentation.context.secrets"), "secrets", message("documentation.context.secrets.description")));
            case "env" -> Optional.of(simpleDoc(message("documentation.context.env"), "env", message("documentation.context.env.description")));
            case "matrix" -> Optional.of(simpleDoc(message("documentation.context.matrix"), "matrix", message("documentation.context.matrix.description")));
            case "steps" -> Optional.of(simpleDoc(message("documentation.context.steps"), "steps", message("documentation.context.steps.description")));
            case "needs" -> Optional.of(simpleDoc(message("documentation.context.needs"), "needs", message("documentation.context.needs.description")));
            case "jobs" -> Optional.of(simpleDoc(message("documentation.context.jobs"), "jobs", message("documentation.context.jobs.description")));
            case "outputs" -> Optional.of(simpleDoc(message("documentation.context.outputs"), "outputs", message("documentation.context.outputs.description")));
            case "result" -> Optional.of(simpleDoc(message("documentation.context.result"), "result", message("documentation.context.result.description")));
            case "outcome" -> Optional.of(simpleDoc(message("documentation.context.outcome"), "outcome", message("documentation.context.outcome.description")));
            case "conclusion" -> Optional.of(simpleDoc(message("documentation.context.conclusion"), "conclusion", message("documentation.context.conclusion.description")));
            default -> Optional.empty();
        };
    }

    private static String message(final String key, final Object... params) {
        return GitHubWorkflowBundle.message(key, params);
    }

    private static DocPayload simpleDoc(final String label, final String name, final String description) {
        final String html = "<h3>" + escape(label) + " <code>" + escape(name) + "</code></h3><p>" + escape(description) + "</p>";
        return new DocPayload(label + " " + name, html, label + " " + name + "\n" + description);
    }

    private static String renderParameter(final String label, final String name, final String details, final String url) {
        final StringBuilder html = new StringBuilder("<h3>")
                .append(escape(label))
                .append(" <code>")
                .append(escape(name))
                .append("</code></h3>");
        for (final String line : detailLines(details)) {
            final int separator = line.indexOf(':');
            if (separator > 0) {
                appendDetail(html, line.substring(0, separator), line.substring(separator + 1).trim());
            } else {
                appendParagraph(html, line);
            }
        }
        appendLink(html, url);
        return html.toString();
    }

    private static String plainParameter(final String label, final String name, final String details) {
        return label + " " + name + (details.isBlank() ? "" : "\n" + details);
    }

    private static List<String> detailLines(final String details) {
        if (details == null || details.isBlank()) {
            return List.of();
        }
        return List.of(details.split("\\R"));
    }

    private static void appendMap(final StringBuilder html, final String title, final java.util.Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }
        html.append("<h4>").append(escape(title)).append("</h4><ul>");
        values.forEach((name, details) -> html.append("<li><code>")
                .append(escape(name))
                .append("</code>")
                .append(details.isBlank() ? "" : " - " + escape(firstLine(details)))
                .append("</li>"));
        html.append("</ul>");
    }

    private static void appendList(final StringBuilder html, final String title, final List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        html.append("<h4>").append(escape(title)).append("</h4><ul>");
        values.forEach(value -> html.append("<li><code>").append(escape(value)).append("</code></li>"));
        html.append("</ul>");
    }

    private static void appendDetail(final StringBuilder html, final String name, final String value) {
        if (!value.isBlank()) {
            html.append("<p><b>").append(escape(name)).append(":</b> ").append(escape(value)).append("</p>");
        }
    }

    private static void appendParagraph(final StringBuilder html, final String value) {
        if (value != null && !value.isBlank()) {
            html.append("<p>").append(escape(value)).append("</p>");
        }
    }

    private static void appendLink(final StringBuilder html, final String url) {
        appendLink(html, url, url);
    }

    private static void appendLink(final StringBuilder html, final String url, final String label) {
        if (url != null && !url.isBlank()) {
            html.append("<p><a href=\"").append(escape(url)).append("\">").append(escape(label)).append("</a></p>");
        }
    }

    private static String firstLine(final String text) {
        return detailLines(text).stream().findFirst().orElse("");
    }

    private static Optional<PsiElement> textElement(final PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getParent() != current) {
            if (WorkflowPsi.isTextElement(current) || current instanceof YAMLScalar) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static Optional<YAMLKeyValue> keyValueAt(final PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getParent() != current) {
            if (current instanceof final YAMLKeyValue keyValue) {
                return Optional.of(keyValue);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static String escape(final String value) {
        return ofNullable(value).orElse("")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    record DocPayload(String title, String html, String hint) {
    }

    private record StepOutputSource(String stepId, String stepName, String usesValue, GitHubAction action) {

        private String stepLabel() {
            if (!stepName.isBlank() && !stepId.isBlank()) {
                return stepName + " (" + stepId + ")";
            }
            if (!stepName.isBlank()) {
                return stepName;
            }
            return stepId;
        }

        private String sourceLabel() {
            if (action == null) {
                return "";
            }
            final String kind = action.isAction()
                    ? message("documentation.externalAction.label")
                    : message("documentation.reusableWorkflow.label");
            final String displayName = action.displayName();
            final String description = action.description();
            return kind + (displayName.isBlank() ? "" : ": " + displayName)
                    + (description.isBlank() ? "" : " - " + description);
        }

        private Optional<String> outputDescription(final String outputName) {
            return ofNullable(action)
                    .filter(GitHubAction::isResolved)
                    .map(GitHubAction::freshOutputs)
                    .map(outputs -> outputs.get(outputName));
        }

        private Optional<String> url() {
            return ofNullable(action)
                    .map(GitHubAction::githubUrl)
                    .filter(url -> !url.isBlank())
                    .or(() -> ofNullable(action)
                            .filter(GitHubAction::isLocal)
                            .map(GitHubAction::downloadUrl)
                            .filter(url -> !url.isBlank()));
        }
    }

    private static final class WorkflowDocumentationElement extends FakePsiElement {
        private final PsiElement delegate;
        private final DocPayload payload;

        private WorkflowDocumentationElement(final PsiElement delegate, final DocPayload payload) {
            this.delegate = delegate;
            this.payload = payload;
        }

        private DocPayload payload() {
            return payload;
        }

        @Override
        public PsiElement getParent() {
            return delegate.getParent();
        }

        @Override
        public PsiFile getContainingFile() {
            return delegate.getContainingFile();
        }

        @Override
        public TextRange getTextRange() {
            return delegate.getTextRange();
        }

        @Override
        public int getTextOffset() {
            return delegate.getTextOffset();
        }

        @Override
        public PsiManager getManager() {
            return delegate.getManager();
        }

        @Override
        public boolean isValid() {
            return delegate.isValid();
        }

        @Override
        public String getName() {
            return payload.title();
        }

        @Override
        public String getText() {
            return payload.title();
        }

        @Override
        public String toString() {
            return "GitHub workflow documentation";
        }
    }
}
