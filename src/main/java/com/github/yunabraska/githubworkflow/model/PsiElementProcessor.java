package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.highlights.IconRenderer;
import com.github.yunabraska.githubworkflow.highlights.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.quickfixes.QuickFixExecution;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.impl.YAMLBlockSequenceImpl;
import org.jetbrains.yaml.psi.impl.YAMLPlainTextImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_USES;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.PATTERN_GITHUB_ENV;
import static com.github.yunabraska.githubworkflow.config.GitHubWorkflowConfig.PATTERN_GITHUB_OUTPUT;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.JUMP_TO_IMPLEMENTATION;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.config.NodeIcon.SETTINGS;
import static com.github.yunabraska.githubworkflow.listeners.ApplicationStartup.asyncInitWorkflowFile;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.hasText;
import static com.github.yunabraska.githubworkflow.model.YamlElementHelper.removeQuotes;
import static com.intellij.lang.annotation.HighlightSeverity.INFORMATION;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

public class PsiElementProcessor {


    public static void processPsiElement(final AnnotationHolder holder, final PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            switch (element.getKeyText()) {
                case FIELD_USES -> usesHandler(holder, element);
                case FIELD_WITH -> withHandler(holder, element);
                case FIELD_NEEDS -> needsHandler(holder, element);
                case FIELD_RUN -> runHandler(holder, element);
                default -> {
                    // No Action
                }
            }
        });
    }

    private static void runHandler(final AnnotationHolder holder, final YAMLKeyValue element) {
        // SHOW Output Env && Output Variable declaration
        ofNullable(element).map(PsiElement::getChildren).map(PsiElementProcessor::getFirstChild).filter(psiElement -> PATTERN_GITHUB_OUTPUT.matcher(psiElement.getText()).find() || PATTERN_GITHUB_ENV.matcher(psiElement.getText()).find()).ifPresent(psiElement -> {
            //TODO: lineWise
            holder.newSilentAnnotation(INFORMATION).gutterIconRenderer(new IconRenderer(null, psiElement, AllIcons.Nodes.Gvariable)).create();
        });
        System.out.println(element);
    }

    private static PsiElement getFirstChild(final PsiElement[] children) {
        return children != null && children.length > 0 ? children[0] : null;
    }

    private static void withHandler(final AnnotationHolder holder, final YAMLKeyValue element) {
        getElementUnderParent(element, FIELD_STEPS)
                .flatMap(step -> getKvChildWithKey(step, FIELD_USES))
                .map(YAMLKeyValue::getValueText)
                .map(YamlElementHelper::removeQuotes)
                .map(GitHubAction::getGitHubAction)
                .map(action -> action.inputsA(() -> element))
                .map(Map::keySet)
                .ifPresent(inputs -> getKvChildren(element).forEach(kvInput -> {
                    if (!inputs.contains(kvInput.getKeyText())) {
                        final TextRange textRange = kvInput.getTextRange();
                        addAnnotation(holder, kvInput, new SyntaxAnnotation(
                                "Delete invalid key [" + kvInput.getKeyText() + "]",
                                null,
                                HighlightSeverity.ERROR,
                                ProblemHighlightType.GENERIC_ERROR,
                                deleteElementAction(textRange)
                        ));
                    }
                }));
    }

    @NotNull
    private static Consumer<QuickFixExecution> deleteElementAction(final TextRange textRange) {
        return fix -> {
            final PsiElement psiElement = fix.file().findElementAt(fix.editor().getCaretModel().getOffset());
            if (psiElement != null) {
                final Document document = PsiDocumentManager.getInstance(fix.project()).getDocument(psiElement.getContainingFile());
                if (document != null) {
                    WriteCommandAction.runWriteCommandAction(fix.project(), () -> document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), ""));
                }
            }
        };
    }

    private static void usesHandler(final AnnotationHolder holder, final YAMLKeyValue element) {
        final List<SyntaxAnnotation> result = new ArrayList<>();
        ofNullable(element.getValueText())
                .map(YamlElementHelper::removeQuotes)
                .filter(YamlElementHelper::hasText)
                .map(GitHubAction::getGitHubAction)
                .ifPresentOrElse(action -> {
                    if (action.isAvailable() && !action.isLocal()) {
                        result.add(newReloadAction(action));
                        result.add(newOpenInBrowserFix("Open in Browser [", action, action.toUrl()));
                        result.add(newOpenInBrowserFix("Open in Marketplace [", action, action.marketplaceUrl()));
                    }
                    if (action.isAvailable() && action.isLocal()) {
                        result.add(newJumpToFile(action));
                    }
                    if (!action.isAvailable()) {
                        result.add(newUnresolvedAction(element));
                    }
                }, () -> result.add(newUnresolvedAction(element)));
        addAnnotation(holder, element, result);
    }

    private static void needsHandler(final AnnotationHolder holder, final YAMLKeyValue element) {
        final List<PsiElement> neededJobs = getTextElements(element);
        if (!neededJobs.isEmpty()) {
            final String currentJobName = getElementUnderParent(element, FIELD_JOBS, YAMLKeyValue.class).map(YAMLKeyValue::getKeyText).orElse("");
            final List<String> previousJobNames = getAllJobs(element).stream().map(YAMLKeyValue::getKeyText).takeWhile(jobName -> !currentJobName.equals(jobName)).toList();
            neededJobs.forEach(neededJob -> {
                final String jobId = removeQuotes(neededJob.getText());
                if (!previousJobNames.contains(jobId)) {
                    // INVALID JOB_ID
                    addAnnotation(holder, neededJob, new SyntaxAnnotation(
                            "Remove invalid jobId [" + jobId + "] - this jobId doesn't match any previous job",
                            null,
                            HighlightSeverity.ERROR,
                            ProblemHighlightType.GENERIC_ERROR,
                            deleteElementAction(neededJob.getTextRange())
                    ));
                }
            });

        }

    }

    public static List<YAMLKeyValue> getAllJobs(final PsiElement psiElement) {
        final List<YAMLKeyValue> result = new ArrayList<>();
        getAllJobs(result, ofNullable(psiElement).map(PsiElement::getContainingFile).orElse(null));
        return unmodifiableList(result);
    }

    private static void getAllJobs(final List<YAMLKeyValue> result, final PsiElement element) {
        if (result == null || element == null) {
            return;
        }
        if (element instanceof final YAMLKeyValue keyValue && FIELD_JOBS.equals(keyValue.getKeyText())) {
            result.addAll(getKvChildren(keyValue));
        } else {
            Arrays.stream(element.getChildren()).forEach(child -> getAllJobs(result, child));
        }
    }

    private static List<String> getPreviousJobNames(final PsiElement psiElement) {
        return getPreviousJobs(psiElement).stream().map(YAMLKeyValue::getKeyText).toList();
    }

    private static List<YAMLKeyValue> getPreviousJobs(final PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : getKvParent(psiElement, FIELD_JOBS)
                .flatMap(jobsWrapper -> getClosestChild(psiElement, jobsWrapper, YAMLKeyValue.class)
                        .map(currentJob -> Optional.of(jobsWrapper)
                                .map(PsiElementProcessor::getKvChildren)
                                .map(kvJobs -> kvJobs.stream()
                                        .takeWhile(job -> job != currentJob)
                                        .toList()
                                )
                                .orElseGet(Collections::emptyList)))
                .orElse(Collections.emptyList());
    }

    private static void addAnnotation(final AnnotationHolder holder, final PsiElement element, final SyntaxAnnotation result) {
        addAnnotation(holder, element, List.of(result));
    }

    private static void addAnnotation(final AnnotationHolder holder, final PsiElement element, final List<SyntaxAnnotation> result) {
        if (holder != null) {
            result.forEach(annotation -> annotation.createAnnotation(element, holder));
        }
    }

    public static List<YAMLKeyValue> getKvChildren(final PsiElement psiElement) {
        return getChildren(psiElement, YAMLKeyValue.class);
    }

    private static Optional<PsiElement> getTextElement(final PsiElement psiElement) {
        final List<PsiElement> textValues = getTextElements(psiElement);
        return textValues.isEmpty() ? Optional.empty() : Optional.of(textValues.get(0));
    }

    private static List<PsiElement> getTextElements(final PsiElement psiElement) {
        final ArrayList<PsiElement> result = new ArrayList<>();
        getTextElements(result, psiElement);
        return unmodifiableList(result);
    }

    private static void getTextElements(final List<PsiElement> result, final PsiElement psiElement) {
        ofNullable(psiElement).ifPresent(element -> {
            if (element instanceof YAMLPlainTextImpl || element instanceof YAMLQuotedText) {
                if (hasText(element.getText())) {
                    result.add(element);
                }
            } else {
                Arrays.stream(element.getChildren()).forEach(child -> getTextElements(result, child));
            }
        });
    }

    public static <T> List<T> getChildren(final PsiElement psiElement, final Class<T> clazz) {
        return ofNullable(psiElement)
                .map(PsiElement::getChildren)
                .map(psiElements -> Arrays.stream(psiElements).filter(clazz::isInstance).map(clazz::cast).toList())
                .filter(children -> !children.isEmpty())
                .or(() -> ofNullable(psiElement).map(PsiElement::getChildren).map(PsiElementProcessor::getFirstChild).map(child -> getChildren(child, clazz)))
                .orElseGet(Collections::emptyList);
    }

    public static Optional<YAMLKeyValue> getKvChildWithKey(final PsiElement psiElement, final String childKey) {
        return psiElement == null || childKey == null ? Optional.empty() : Optional.of(psiElement)
                .map(PsiElementProcessor::getKvChildren)
                .flatMap(children -> children.stream()
                        .filter(Objects::nonNull)
                        .filter(child -> childKey.equals(child.getKeyText()))
                        .findFirst()
                );
    }

    public static Optional<PsiElement> getElementUnderParent(final PsiElement psiElement, final String keyName) {
        return getElementUnderParent(psiElement, keyName, PsiElement.class);
    }

    public static <T extends PsiElement> Optional<T> getElementUnderParent(final PsiElement psiElement, final String keyName, final Class<T> clazz) {
        return psiElement == null || keyName == null ? Optional.empty() : getKvParent(psiElement, yamlKeyValue -> keyName.equals(yamlKeyValue.getKeyText()))
                .flatMap(yamlKeyValue -> getClosestChild(psiElement, yamlKeyValue, clazz));
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

    private static Optional<YAMLKeyValue> getKvParent(final PsiElement psiElement, final String fieldKey) {
        return psiElement == null || fieldKey == null ? Optional.empty() : getKvParent(psiElement, parent -> fieldKey.equals(parent.getKeyText()));
    }

    private static Optional<YAMLKeyValue> getKvParent(final PsiElement psiElement, final Predicate<YAMLKeyValue> filter) {
        return psiElement == null || filter == null ? Optional.empty() : Optional.of(psiElement)
                .flatMap(PsiElementProcessor::toYAMLKeyValue)
                .filter(filter)
                .or(() -> Optional.of(psiElement)
                        .map(PsiElement::getParent)
                        .flatMap(parent -> getKvParent(parent, filter))
                );
    }

    private static Optional<YAMLKeyValue> toYAMLKeyValue(final PsiElement psiElement) {
        if (psiElement instanceof final YAMLKeyValue element) {
            return Optional.of(element);
        }
        return Optional.empty();
    }

    public static Project mapToProject(final PsiElement currentElement) {
        return mapToProject(() -> currentElement);
    }

    public static Project mapToProject(final Supplier<PsiElement> currentElement) {
        return ofNullable(currentElement)
                .map(Supplier::get)
                .map(PsiElement::getContainingFile)
                .map(PsiElement::getProject)
                .orElse(null);
    }

    @NotNull
    private static SyntaxAnnotation newReloadAction(final GitHubAction action) {
        return new SyntaxAnnotation(
                "Reload [" + ofNullable(action.slug()).orElseGet(action::uses) + "]",
                RELOAD,
                INFORMATION,
                ProblemHighlightType.INFORMATION,
                f -> {
                    action.deleteCache();
                    asyncInitWorkflowFile(f.project(), f.file().getVirtualFile());
                }
        );
    }

    @NotNull
    private static SyntaxAnnotation newUnresolvedAction(final YAMLKeyValue element) {
        //TODO: option to not ask again
        return new SyntaxAnnotation(
                "Unresolved [" + removeQuotes(element.getValueText()) + "] - you may need to connect your GitHub",
                SETTINGS,
                HighlightSeverity.WEAK_WARNING,
                ProblemHighlightType.WEAK_WARNING,
                f -> {
                    ShowSettingsUtil.getInstance().showSettingsDialog(f.project(), "GitHub");
                    asyncInitWorkflowFile(f.project(), f.file().getVirtualFile());
                }
        );
    }

    @NotNull
    private static SyntaxAnnotation newJumpToFile(final GitHubAction action) {
        return new SyntaxAnnotation(
                "Jump to file [" + ofNullable(action.slug()).orElseGet(action::uses) + "]",
                JUMP_TO_IMPLEMENTATION,
                INFORMATION,
                ProblemHighlightType.INFORMATION,
                f -> Optional.of(action)
                        .map(a -> a.getLocalPath(f.project()))
                        .map(path -> LocalFileSystem.getInstance().findFileByPath(path))
                        .map(target -> PsiManager.getInstance(f.project()).findFile(target))
                        .ifPresent(psiFile -> {
                            // Navigate to PsiElement
                            PsiNavigationSupport.getInstance().createNavigatable(f.project(), psiFile.getVirtualFile(), 0).navigate(true);
                        })
        );
    }

    @NotNull
    private static SyntaxAnnotation newOpenInBrowserFix(final String text, final GitHubAction action, final String url) {
        return new SyntaxAnnotation(
                text + ofNullable(action.slug()).orElseGet(action::uses) + "]",
                null,
                INFORMATION,
                ProblemHighlightType.INFORMATION,
                quickFixExecution -> BrowserUtil.browse(url)
        );
    }
}
