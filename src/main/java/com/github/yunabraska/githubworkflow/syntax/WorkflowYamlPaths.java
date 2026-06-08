package com.github.yunabraska.githubworkflow.syntax;

import java.util.List;

public class WorkflowYamlPaths {

    private WorkflowYamlPaths() {
    }

    public static boolean pathEndsWith(final List<String> path, final String... expected) {
        if (path.size() < expected.length) {
            return false;
        }
        final int offset = path.size() - expected.length;
        for (int index = 0; index < expected.length; index++) {
            if (!expected[index].equals(path.get(offset + index))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isChildOf(final List<String> path, final String... expectedParent) {
        return path.size() == expectedParent.length + 1 && pathEndsWith(path.subList(0, path.size() - 1), expectedParent);
    }

    public static boolean pathMatches(final List<String> path, final String... pattern) {
        if (path.size() != pattern.length) {
            return false;
        }
        for (int index = 0; index < pattern.length; index++) {
            if (!"*".equals(pattern[index]) && !pattern[index].equals(path.get(index))) {
                return false;
            }
        }
        return true;
    }
}
