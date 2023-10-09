package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.github.yunabraska.githubworkflow.services.SchemaProvider.SCHEMA_FILE_PROVIDERS;

public class FileIconProvider extends IconProvider {

    @Nullable
    @Override
    @SuppressWarnings("java:S2637")
    public Icon getIcon(@NotNull final PsiElement element, final int flags) {
        return SCHEMA_FILE_PROVIDERS.stream()
                .filter(GitHubSchemaProvider.class::isInstance)
                .map(GitHubSchemaProvider.class::cast)
                .filter(schemaProvider -> schemaProvider.isValidFile(element))
                .map(schema -> AllIcons.Vcs.Vendors.Github)
                .findFirst()
                .orElse(null);
    }
}
