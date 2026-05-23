package com.github.yunabraska.githubworkflow.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts raw GitHub Actions log lines into compact IDE console segments.
 */
final class WorkflowRunLogRenderer {

    private static final Pattern TIMESTAMP = Pattern.compile("^\\x{FEFF}?\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\\s+");
    private static final Pattern GITHUB_COMMAND = Pattern.compile("^##\\[([^]]+)](.*)$");
    private static final Pattern WORKFLOW_COMMAND = Pattern.compile("^::([^: ]+)(?: [^:]*)?::(.*)$");
    private static final Pattern ANSI_SGR = Pattern.compile("\\x1B\\[([0-9;]*)m");
    private static final Pattern ANSI_CONTROL = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

    private int lineNumber = 0;
    private boolean printedAny = false;

    static List<Segment> renderOnce(final String text) {
        return new WorkflowRunLogRenderer().render(text);
    }

    static String renderPlainOnce(final String text) {
        return new WorkflowRunLogRenderer().renderPlain(text);
    }

    List<Segment> render(final String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        final List<Segment> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            final int next = nextLineEnd(text, start);
            appendLine(result, text.substring(start, next));
            start = next;
        }
        return List.copyOf(result);
    }

    String renderPlain(final String text) {
        final StringBuilder result = new StringBuilder();
        for (final Segment segment : render(text)) {
            result.append(segment.text());
        }
        return result.toString();
    }

    private void appendLine(final List<Segment> result, final String rawLine) {
        final LineParts parts = splitLine(rawLine);
        final AnsiLine ansiLine = stripAnsi(TIMESTAMP.matcher(parts.text()).replaceFirst(""));
        final String line = ansiLine.text();
        final Matcher githubCommand = GITHUB_COMMAND.matcher(line);
        if (githubCommand.matches()) {
            appendGitHubCommand(result, githubCommand.group(1), githubCommand.group(2), parts.separator());
            return;
        }
        final Matcher workflowCommand = WORKFLOW_COMMAND.matcher(line);
        if (workflowCommand.matches()) {
            appendWorkflowCommand(result, workflowCommand.group(1), workflowCommand.group(2), parts.separator());
            return;
        }
        appendNumbered(result, line, ansiLine.kind() == Kind.NORMAL ? inferredKind(line) : ansiLine.kind(), parts.separator());
    }

    private void appendGitHubCommand(final List<Segment> result, final String command, final String value, final String separator) {
        final String name = commandName(command);
        switch (name) {
            case "group" -> appendBlockHeader(result, value);
            case "endgroup", "/group" -> appendBlockEnd();
            case "command" -> appendNumbered(result, label("workflow.log.command") + " " + value, Kind.SYSTEM, separator);
            case "warning" -> appendNumbered(result, label("workflow.log.warning") + " " + value, Kind.WARNING, separator);
            case "error" -> appendNumbered(result, label("workflow.log.error") + " " + value, Kind.ERROR, separator);
            default -> appendNumbered(result, value.isBlank() ? "[" + name + "]" : value, Kind.SYSTEM, separator);
        }
    }

    private void appendWorkflowCommand(final List<Segment> result, final String command, final String value, final String separator) {
        final String name = commandName(command);
        switch (name) {
            case "warning" -> appendNumbered(result, label("workflow.log.warning") + " " + value, Kind.WARNING, separator);
            case "error" -> appendNumbered(result, label("workflow.log.error") + " " + value, Kind.ERROR, separator);
            case "group" -> appendBlockHeader(result, value);
            case "endgroup", "/group" -> appendBlockEnd();
            default -> appendNumbered(result, value, Kind.SYSTEM, separator);
        }
    }

    private void appendBlockHeader(final List<Segment> result, final String title) {
        final String prefix = printedAny ? "\n" : "";
        result.add(new Segment(prefix + "== " + title.strip() + " ==\n", Kind.SYSTEM));
        lineNumber = 0;
        printedAny = true;
    }

    private void appendBlockEnd() {
        lineNumber = 0;
    }

    private void appendNumbered(final List<Segment> result, final String line, final Kind kind, final String separator) {
        printedAny = true;
        if (line.isBlank()) {
            result.add(new Segment(separator, kind));
            return;
        }
        lineNumber++;
        result.add(new Segment(String.format(Locale.ROOT, "%04d | %s%s", lineNumber, line, separator), kind));
    }

    private static AnsiLine stripAnsi(final String line) {
        Kind kind = Kind.NORMAL;
        final Matcher matcher = ANSI_SGR.matcher(line);
        while (matcher.find()) {
            kind = strongest(kind, kindForAnsi(matcher.group(1)));
        }
        return new AnsiLine(ANSI_CONTROL.matcher(line).replaceAll(""), kind);
    }

    private static Kind kindForAnsi(final String value) {
        final String[] codes = value.isBlank() ? new String[]{"0"} : value.split(";");
        Kind result = Kind.NORMAL;
        for (final String code : codes) {
            result = strongest(result, switch (code) {
                case "31", "91" -> Kind.ERROR;
                case "33", "93" -> Kind.WARNING;
                case "34", "35", "36", "90", "94", "95", "96" -> Kind.SYSTEM;
                default -> Kind.NORMAL;
            });
        }
        return result;
    }

    private static Kind strongest(final Kind left, final Kind right) {
        return weight(right) > weight(left) ? right : left;
    }

    private static int weight(final Kind kind) {
        return switch (kind) {
            case ERROR -> 3;
            case WARNING -> 2;
            case SYSTEM -> 1;
            case NORMAL -> 0;
        };
    }

    private static Kind inferredKind(final String line) {
        final String normalized = line.stripLeading().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("error:") || normalized.startsWith("fatal:")) {
            return Kind.ERROR;
        }
        if (normalized.startsWith("warning:") || normalized.startsWith("npm warn ")) {
            return Kind.WARNING;
        }
        return Kind.NORMAL;
    }

    private static String commandName(final String command) {
        final int space = command.indexOf(' ');
        return (space >= 0 ? command.substring(0, space) : command).toLowerCase(Locale.ROOT);
    }

    private static String label(final String key) {
        return GitHubWorkflowBundle.message(key);
    }

    private static LineParts splitLine(final String line) {
        if (line.endsWith("\r\n")) {
            return new LineParts(line.substring(0, line.length() - 2), "\r\n");
        }
        if (line.endsWith("\n") || line.endsWith("\r")) {
            return new LineParts(line.substring(0, line.length() - 1), line.substring(line.length() - 1));
        }
        return new LineParts(line, "");
    }

    private static int nextLineEnd(final String text, final int start) {
        int index = start;
        while (index < text.length() && text.charAt(index) != '\n' && text.charAt(index) != '\r') {
            index++;
        }
        if (index >= text.length()) {
            return index;
        }
        if (text.charAt(index) == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
            return index + 2;
        }
        return index + 1;
    }

    enum Kind {
        NORMAL,
        SYSTEM,
        WARNING,
        ERROR
    }

    record Segment(String text, Kind kind) {
    }

    private record AnsiLine(String text, Kind kind) {
    }

    private record LineParts(String text, String separator) {
    }
}
