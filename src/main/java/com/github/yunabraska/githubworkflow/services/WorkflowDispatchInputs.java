package com.github.yunabraska.githubworkflow.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight reader for workflow_dispatch inputs used by run configuration defaults.
 */
public final class WorkflowDispatchInputs {

    public List<Input> parse(final String yaml) {
        final List<Line> lines = lines(yaml);
        final Optional<Integer> workflowDispatchIndex = workflowDispatchIndex(lines);
        if (workflowDispatchIndex.isEmpty()) {
            return List.of();
        }
        final int workflowDispatchIndent = lines.get(workflowDispatchIndex.get()).indent();
        final Optional<Integer> inputsIndex = childIndex(lines, workflowDispatchIndex.get() + 1, workflowDispatchIndent, "inputs");
        if (inputsIndex.isEmpty()) {
            return List.of();
        }
        final int inputsIndent = lines.get(inputsIndex.get()).indent();
        final List<Input> result = new ArrayList<>();
        for (int index = inputsIndex.get() + 1; index < lines.size(); index++) {
            final Line line = lines.get(index);
            if (line.indent() <= inputsIndent) {
                break;
            }
            if (line.indent() == inputsIndent + 2 && line.keyValue().isPresent()) {
                result.add(readInput(lines, index, inputsIndent + 2));
            }
        }
        return List.copyOf(result);
    }

    public boolean hasWorkflowDispatch(final String yaml) {
        return workflowDispatchIndex(lines(yaml)).isPresent();
    }

    public String defaultsText(final String yaml) {
        final StringBuilder result = new StringBuilder();
        for (final Input input : parse(yaml)) {
            result.append(input.name()).append("=").append(input.defaultValue()).append("\n");
        }
        return result.toString();
    }

    public static java.util.Map<String, String> parseKeyValueText(final String text) {
        final java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        Optional.ofNullable(text).orElse("").lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .forEach(line -> {
                    final int separator = line.indexOf('=');
                    if (separator > 0) {
                        result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                    }
                });
        return java.util.Map.copyOf(result);
    }

    private static Input readInput(final List<Line> lines, final int inputIndex, final int inputIndent) {
        final String name = lines.get(inputIndex).keyValue().orElse("");
        String type = "string";
        String required = "false";
        String defaultValue = "";
        String description = "";
        final List<String> options = new ArrayList<>();
        for (int index = inputIndex + 1; index < lines.size(); index++) {
            final Line line = lines.get(index);
            if (line.indent() <= inputIndent) {
                break;
            }
            if (line.indent() == inputIndent + 2) {
                if ("type".equals(line.keyValue().orElse(""))) {
                    type = line.value();
                } else if ("required".equals(line.keyValue().orElse(""))) {
                    required = line.value();
                } else if ("default".equals(line.keyValue().orElse(""))) {
                    defaultValue = line.value();
                } else if ("description".equals(line.keyValue().orElse(""))) {
                    description = line.value();
                } else if ("options".equals(line.keyValue().orElse(""))) {
                    options.addAll(readOptions(lines, index, inputIndent + 2));
                }
            }
        }
        return new Input(name, type, Boolean.parseBoolean(required), defaultValue, description, List.copyOf(options));
    }

    private static List<String> readOptions(final List<Line> lines, final int optionsIndex, final int optionsIndent) {
        final List<String> result = new ArrayList<>(inlineOptions(lines.get(optionsIndex).value()));
        for (int index = optionsIndex + 1; index < lines.size(); index++) {
            final Line line = lines.get(index);
            if (line.indent() <= optionsIndent) {
                break;
            }
            if (line.content().startsWith("- ")) {
                result.add(stripQuotes(line.content().substring(2).trim()));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> inlineOptions(final String value) {
        final String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of();
        }
        final String body = trimmed.substring(1, trimmed.length() - 1);
        if (body.isBlank()) {
            return List.of();
        }
        return splitInlineList(body).stream()
                .filter(option -> !option.isBlank())
                .map(WorkflowDispatchInputs::stripQuotes)
                .toList();
    }

    private static List<String> splitInlineList(final String body) {
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int index = 0; index < body.length(); index++) {
            final char character = body.charAt(index);
            if (quote != 0) {
                current.append(character);
                if (character == quote) {
                    quote = 0;
                }
            } else if (character == '\'' || character == '"') {
                quote = character;
                current.append(character);
            } else if (character == ',') {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        result.add(current.toString().trim());
        return List.copyOf(result);
    }

    private static Optional<Integer> workflowDispatchIndex(final List<Line> lines) {
        for (int index = 0; index < lines.size(); index++) {
            final Line line = lines.get(index);
            if ("workflow_dispatch".equals(line.keyValue().orElse("")) || "on".equals(line.keyValue().orElse("")) && "workflow_dispatch".equals(line.value())) {
                return Optional.of(index);
            }
            if (line.content().equals("- workflow_dispatch")) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> childIndex(final List<Line> lines, final int start, final int parentIndent, final String key) {
        for (int index = start; index < lines.size(); index++) {
            final Line line = lines.get(index);
            if (line.indent() <= parentIndent) {
                break;
            }
            if (key.equals(line.keyValue().orElse(""))) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private static List<Line> lines(final String yaml) {
        final List<Line> result = new ArrayList<>();
        Optional.ofNullable(yaml).orElse("").lines()
                .map(WorkflowDispatchInputs::line)
                .filter(line -> !line.content().isBlank())
                .filter(line -> !line.content().startsWith("#"))
                .forEach(result::add);
        return result;
    }

    private static Line line(final String raw) {
        int indent = 0;
        while (indent < raw.length() && raw.charAt(indent) == ' ') {
            indent++;
        }
        final String content = raw.substring(indent).trim();
        final int separator = content.indexOf(':');
        if (separator < 0) {
            return new Line(indent, content, "", "");
        }
        final String key = content.substring(0, separator).trim();
        final String value = stripQuotes(content.substring(separator + 1).trim());
        return new Line(indent, content, key, value);
    }

    private static String stripQuotes(final String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public record Input(String name, String type, boolean required, String defaultValue, String description, List<String> options) {
        public Input(
                final String name,
                final String type,
                final boolean required,
                final String defaultValue,
                final String description
        ) {
            this(name, type, required, defaultValue, description, List.of());
        }

        public Input {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    private record Line(int indent, String content, String key, String value) {
        Optional<String> keyValue() {
            return key.isBlank() ? Optional.empty() : Optional.of(key);
        }
    }
}
