package com.github.yunabraska.githubworkflowplugin.language;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

public class GitHubWorkflowFileTypeFactory extends FileTypeFactory {
    @Override
    public void createFileTypes(@NotNull final FileTypeConsumer consumer) {
        consumer.consume(GitHubWorkflowFileType.INSTANCE, "yml;yaml;YML;YAML");
    }
}

