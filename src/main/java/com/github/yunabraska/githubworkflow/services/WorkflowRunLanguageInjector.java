package com.github.yunabraska.githubworkflow.services;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.helper.GitHubWorkflowConfig.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getChild;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentJob;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getParentStep;
import static com.github.yunabraska.githubworkflow.helper.PsiElementHelper.getText;

public final class WorkflowRunLanguageInjector implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
        if (!(context instanceof YAMLScalar scalar) || !isRunScalar(scalar)) {
            return;
        }
        languageForShell(scalar)
                .ifPresent(language -> inject(registrar, scalar, language));
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(YAMLScalar.class);
    }

    private static boolean isRunScalar(final YAMLScalar scalar) {
        return scalar.getParent() instanceof YAMLKeyValue keyValue && FIELD_RUN.equals(keyValue.getKeyText());
    }

    private static Optional<Language> languageForShell(final YAMLScalar scalar) {
        return shellFor(scalar)
                .map(WorkflowRunLanguageInjector::languageId)
                .flatMap(id -> Optional.ofNullable(Language.findLanguageByID(id)));
    }

    private static Optional<String> shellFor(final YAMLScalar scalar) {
        return getParentStep(scalar)
                .flatMap(step -> getText(step, "shell"))
                .or(() -> getParentJob(scalar)
                        .flatMap(job -> getChild(job, "defaults"))
                        .flatMap(defaults -> getChild(defaults, FIELD_RUN))
                        .flatMap(run -> getText(run, "shell")))
                .or(() -> getChild(scalar.getContainingFile(), "defaults")
                        .flatMap(defaults -> getChild(defaults, FIELD_RUN))
                        .flatMap(run -> getText(run, "shell")))
                .or(() -> Optional.of("bash"));
    }

    private static String languageId(final String shell) {
        final String normalized = shell.toLowerCase(Locale.ROOT).trim();
        if (normalized.contains("pwsh") || normalized.contains("powershell")) {
            return "PowerShell";
        }
        if (normalized.contains("python")) {
            return "Python";
        }
        if (normalized.contains("node") || normalized.contains("javascript") || normalized.equals("js")) {
            return "JavaScript";
        }
        if (normalized.contains("ruby")) {
            return "Ruby";
        }
        if (normalized.contains("perl")) {
            return "Perl";
        }
        return "Shell Script";
    }

    private static void inject(final MultiHostRegistrar registrar, final YAMLScalar scalar, final Language language) {
        final List<TextRange> ranges = contentRanges(scalar);
        if (ranges.isEmpty()) {
            return;
        }
        registrar.startInjecting(language);
        ranges.forEach(range -> registrar.addPlace(null, null, scalar, range));
        registrar.doneInjecting();
    }

    private static List<TextRange> contentRanges(final YAMLScalar scalar) {
        final List<TextRange> ranges = scalar instanceof YAMLScalarImpl scalarImpl
                ? scalarImpl.getContentRanges()
                : fallbackContentRanges(scalar);
        final List<TextRange> withoutExpressions = ranges.stream()
                .flatMap(range -> excludeWorkflowExpressions(scalar.getText(), range).stream())
                .toList();
        return subtractRanges(withoutExpressions, hereDocBodyRanges(scalar.getText(), new TextRange(0, scalar.getTextLength()))).stream()
                .filter(range -> range.getStartOffset() < range.getEndOffset())
                .toList();
    }

    private static List<TextRange> fallbackContentRanges(final YAMLScalar scalar) {
        final int length = scalar.getTextLength();
        return length == 0 ? List.of() : List.of(new TextRange(0, length));
    }

    private static List<TextRange> excludeWorkflowExpressions(final String text, final TextRange range) {
        final java.util.ArrayList<TextRange> result = new java.util.ArrayList<>();
        int start = range.getStartOffset();
        while (start < range.getEndOffset()) {
            final int expressionStart = text.indexOf("${{", start);
            if (expressionStart < 0 || expressionStart >= range.getEndOffset()) {
                result.add(new TextRange(start, range.getEndOffset()));
                break;
            }
            if (start < expressionStart) {
                result.add(new TextRange(start, expressionStart));
            }
            final int expressionEnd = text.indexOf("}}", expressionStart + 3);
            start = expressionEnd < 0 ? range.getEndOffset() : Math.min(expressionEnd + 2, range.getEndOffset());
        }
        return result;
    }

    private static List<TextRange> hereDocBodyRanges(final String text, final TextRange range) {
        final java.util.ArrayList<TextRange> result = new java.util.ArrayList<>();
        String delimiter = "";
        int bodyStart = -1;
        int lineStart = range.getStartOffset();
        while (lineStart < range.getEndOffset()) {
            final int newline = text.indexOf('\n', lineStart);
            final int lineEnd = newline < 0 ? range.getEndOffset() : Math.min(newline, range.getEndOffset());
            final String line = text.substring(lineStart, lineEnd);
            if (delimiter.isBlank()) {
                final Optional<String> nextDelimiter = hereDocDelimiter(line);
                if (nextDelimiter.isPresent()) {
                    delimiter = nextDelimiter.get();
                    bodyStart = Math.min(lineEnd + 1, range.getEndOffset());
                }
            } else if (line.trim().equals(delimiter)) {
                if (bodyStart >= 0 && bodyStart < lineStart) {
                    result.add(new TextRange(bodyStart, lineStart));
                }
                delimiter = "";
                bodyStart = -1;
            }
            if (newline < 0 || lineEnd >= range.getEndOffset()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        if (!delimiter.isBlank() && bodyStart >= 0 && bodyStart < range.getEndOffset()) {
            result.add(new TextRange(bodyStart, range.getEndOffset()));
        }
        return result;
    }

    private static Optional<String> hereDocDelimiter(final String line) {
        char quote = 0;
        for (int index = 0; index + 1 < line.length(); index++) {
            final char current = line.charAt(index);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '<' && line.charAt(index + 1) == '<') {
                int delimiterStart = index + 2;
                if (delimiterStart < line.length() && line.charAt(delimiterStart) == '-') {
                    delimiterStart++;
                }
                while (delimiterStart < line.length() && Character.isWhitespace(line.charAt(delimiterStart))) {
                    delimiterStart++;
                }
                int delimiterEnd = delimiterStart;
                while (delimiterEnd < line.length() && isDelimiterChar(line.charAt(delimiterEnd))) {
                    delimiterEnd++;
                }
                if (delimiterStart < delimiterEnd) {
                    return Optional.of(line.substring(delimiterStart, delimiterEnd));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isDelimiterChar(final char character) {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static List<TextRange> subtractRanges(final List<TextRange> ranges, final List<TextRange> excludedRanges) {
        List<TextRange> result = ranges;
        for (final TextRange excludedRange : excludedRanges) {
            result = result.stream()
                    .flatMap(range -> subtractRange(range, excludedRange).stream())
                    .toList();
        }
        return result;
    }

    private static List<TextRange> subtractRange(final TextRange range, final TextRange excludedRange) {
        if (!range.intersectsStrict(excludedRange)) {
            return List.of(range);
        }
        final java.util.ArrayList<TextRange> result = new java.util.ArrayList<>();
        if (range.getStartOffset() < excludedRange.getStartOffset()) {
            result.add(new TextRange(range.getStartOffset(), excludedRange.getStartOffset()));
        }
        if (excludedRange.getEndOffset() < range.getEndOffset()) {
            result.add(new TextRange(excludedRange.getEndOffset(), range.getEndOffset()));
        }
        return result;
    }
}
