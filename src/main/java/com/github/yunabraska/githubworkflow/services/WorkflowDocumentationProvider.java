package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
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

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_ON;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParent;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getText;
import static com.github.yunabraska.githubworkflow.logic.Steps.listStepOutputs;
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
        return ofNullable(details)
                .map(text -> new DocPayload(
                        (secret ? "Secret " : "Input ") + name,
                        renderParameter(secret ? "Secret" : "Input", name, text, action.githubUrl()),
                        plainParameter(secret ? "Secret" : "Input", name, text)
                ));
    }

    private static Optional<DocPayload> variableDoc(final PsiElement textElement, final int absoluteOffset) {
        final int offsetInElement = absoluteOffset - textElement.getTextRange().getStartOffset();
        final Optional<ExpressionReferenceTarget> target = ExpressionReferenceTargets.resolveAt(textElement, offsetInElement).stream().findFirst();
        if (target.isPresent()) {
            return Optional.of(referenceDoc(target.get()));
        }
        return ExpressionReferenceTargets.segmentAt(textElement, offsetInElement)
                .flatMap(WorkflowDocumentationProvider::contextDoc);
    }

    private static DocPayload referenceDoc(final ExpressionReferenceTarget target) {
        return switch (target.kind()) {
            case "input" -> yamlParameterDoc("Input", target.segment().text(), target.target());
            case "secret" -> yamlParameterDoc("Secret", target.segment().text(), target.target());
            case "env" -> yamlValueDoc("Environment variable", target.segment().text(), target.target());
            case "matrix" -> yamlValueDoc("Matrix property", target.segment().text(), target.target());
            case "step" -> stepDoc(target.target());
            case "step-output" -> stepOutputDoc(target.segment().text(), target.target());
            case "need" -> simpleDoc("Needed job", target.segment().text(), "Direct job dependency.");
            case "need-output" -> outputDoc("Needed job output", target.segment().text(), target.target());
            case "job" -> simpleDoc("Reusable workflow job", target.segment().text(), "Job declared in this reusable workflow.");
            case "job-output" -> outputDoc("Reusable workflow job output", target.segment().text(), target.target());
            case "service" -> yamlValueDoc("Service container", target.segment().text(), target.target());
            case "service-port" -> yamlValueDoc("Service port", target.segment().text(), target.target());
            case "container" -> yamlValueDoc("Job container", target.segment().text(), target.target());
            default -> simpleDoc("Workflow symbol", target.segment().text(), "Resolved workflow expression.");
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
        return getParent(output, FIELD_ON).isPresent() ? "Workflow output" : "Job output";
    }

    private static DocPayload actionDoc(final GitHubAction action) {
        final String title = action.displayName();
        final StringBuilder html = new StringBuilder();
        html.append("<h3>").append(escape(title)).append("</h3>");
        html.append("<p><b>").append(action.isAction() ? "Action" : "Reusable workflow").append("</b>");
        if (action.isResolved()) {
            html.append(" resolved from <code>").append(escape(action.usesValue())).append("</code>");
        } else {
            html.append(" not resolved yet");
        }
        html.append("</p>");
        appendParagraph(html, action.description());
        appendLink(html, action.githubUrl());
        appendMap(html, "Inputs", action.freshInputs());
        appendMap(html, "Outputs", action.freshOutputs());
        appendMap(html, "Secrets", action.freshSecrets());
        return new DocPayload(title, html.toString(), title + "\n" + action.usesValue());
    }

    private static DocPayload yamlParameterDoc(final String label, final String name, final PsiElement target) {
        final String details = target instanceof YAMLKeyValue keyValue
                ? PsiElementHelper.getDescription(keyValue, "Input".equals(label))
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
                + (value.isBlank() ? "" : "<p><b>Value:</b> <code>" + escape(value) + "</code></p>");
        return new DocPayload(label + " " + name, html, label + " " + name);
    }

    private static DocPayload stepDoc(final PsiElement target) {
        final YAMLKeyValue id = target instanceof YAMLKeyValue keyValue ? keyValue : null;
        final Optional<org.jetbrains.yaml.psi.YAMLSequenceItem> stepItem = PsiElementHelper.getParentStep(target);
        final String name = id == null ? target.getText() : getText(id).orElse(id.getKeyText());
        final String title = "Step " + name;
        final StringBuilder html = new StringBuilder("<h3>").append(escape(title)).append("</h3>");
        stepItem.flatMap(step -> getChild(step, "name")).flatMap(PsiElementHelper::getText).ifPresent(value -> appendDetail(html, "Name", value));
        stepItem.flatMap(step -> getChild(step, FIELD_USES)).flatMap(PsiElementHelper::getText).ifPresent(value -> appendDetail(html, "Uses", value));
        stepItem.flatMap(step -> getChild(step, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> action != null && action.isResolved())
                .ifPresent(action -> {
                    appendDetail(html, action.isAction() ? "Action" : "Reusable workflow", action.displayName());
                    appendParagraph(html, action.description());
                });
        stepItem.flatMap(step -> getChild(step, "run")).flatMap(PsiElementHelper::getText).ifPresent(value -> appendDetail(html, "Run", value));
        stepItem.ifPresent(step -> appendList(html, "Outputs", listStepOutputs(step).stream().map(output -> output.key()).toList()));
        return new DocPayload(title, html.toString(), title);
    }

    private static DocPayload stepOutputDoc(final String outputName, final PsiElement target) {
        final Optional<StepOutputSource> source = stepOutputSource(target);
        final StringBuilder details = new StringBuilder();
        source.flatMap(value -> value.outputDescription(outputName)).ifPresent(value -> appendLine(details, "Description", value));
        source.ifPresent(value -> {
            appendLine(details, "Step", value.stepLabel());
            appendLine(details, "Uses", value.usesValue());
            appendLine(details, "Source", value.sourceLabel());
        });
        final String plainDetails = details.toString();
        final StringBuilder html = new StringBuilder(renderParameter("Step output", outputName, plainDetails, ""));
        source.flatMap(StepOutputSource::url).ifPresent(url -> appendLink(html, url, source.map(StepOutputSource::usesValue).orElse(url)));
        return new DocPayload(
                "Step output " + outputName,
                html.toString(),
                plainParameter("Step output", outputName, plainDetails)
        );
    }

    private static Optional<StepOutputSource> stepOutputSource(final PsiElement target) {
        final Optional<org.jetbrains.yaml.psi.YAMLSequenceItem> stepItem = PsiElementHelper.getParentStep(target);
        return stepItem.map(step -> {
            final String stepId = getText(step, "id").orElse("");
            final String stepName = getText(step, "name").orElse("");
            final Optional<YAMLKeyValue> uses = getChild(step, FIELD_USES);
            final GitHubAction action = uses.map(GitHubActionCache::getAction)
                    .filter(candidate -> candidate != null && candidate.isResolved())
                    .orElse(null);
            return new StepOutputSource(stepId, stepName, uses.flatMap(PsiElementHelper::getText).orElse(""), action);
        });
    }

    private static DocPayload outputDoc(final String label, final String name, final PsiElement target) {
        final StringBuilder details = new StringBuilder();
        keyValueAt(target).ifPresent(output -> {
            appendLine(details, "Description", getText(output, "description").orElse(""));
            appendLine(details, "Value", getText(output, "value").or(() -> getText(output)).orElse(""));
            outputSourceDetails(output).ifPresent(source -> appendLine(details, "Source", source));
        });
        return new DocPayload(
                label + " " + name,
                renderParameter(label, name, details.toString(), ""),
                plainParameter(label, name, details.toString())
        );
    }

    private static Optional<String> outputSourceDetails(final YAMLKeyValue output) {
        return PsiElementHelper.getTextElement(output)
                .stream()
                .flatMap(text -> ExpressionReferenceTargets.resolve(text).stream())
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
            case "github" -> Optional.of(simpleDoc("github context", "github", "Information about the current workflow run and event."));
            case "gitea" -> Optional.of(simpleDoc("gitea context", "gitea", "Gitea-compatible alias for the GitHub Actions context."));
            case "inputs" -> Optional.of(simpleDoc("inputs context", "inputs", "Workflow, dispatch, or action inputs available here."));
            case "secrets" -> Optional.of(simpleDoc("secrets context", "secrets", "Secret values available to this workflow or reusable workflow call."));
            case "env" -> Optional.of(simpleDoc("env context", "env", "Environment variables visible at this location."));
            case "matrix" -> Optional.of(simpleDoc("matrix context", "matrix", "Matrix values for the current job."));
            case "steps" -> Optional.of(simpleDoc("steps context", "steps", "Previous steps in the current job, including outputs and status."));
            case "needs" -> Optional.of(simpleDoc("needs context", "needs", "Direct job dependencies and their outputs/results."));
            case "jobs" -> Optional.of(simpleDoc("jobs context", "jobs", "Reusable workflow jobs and outputs."));
            case "outputs" -> Optional.of(simpleDoc("outputs", "outputs", "Output values exposed by this step or job."));
            case "result" -> Optional.of(simpleDoc("result", "result", "Job result: success, failure, cancelled, or skipped."));
            case "outcome" -> Optional.of(simpleDoc("outcome", "outcome", "Step result before continue-on-error is applied."));
            case "conclusion" -> Optional.of(simpleDoc("conclusion", "conclusion", "Step result after continue-on-error is applied."));
            default -> Optional.empty();
        };
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
            if (PsiElementHelper.isTextElement(current) || current instanceof YAMLScalar) {
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
            final String kind = action.isAction() ? "External action" : "Reusable workflow";
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
