package com.github.yunabraska.githubworkflow.client;

import java.util.List;

public record RemoteActionResolution(
        String usesValue,
        String name,
        String downloadUrl,
        String githubUrl,
        String content,
        boolean action,
        List<String> refs
) {
}
