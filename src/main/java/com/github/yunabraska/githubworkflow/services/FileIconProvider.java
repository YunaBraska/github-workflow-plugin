package com.github.yunabraska.githubworkflow.services;

import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.services.SchemaProvider.SCHEMA_FILE_PROVIDERS;

public class FileIconProvider extends IconProvider {

    @Nullable
    @Override
    @SuppressWarnings("java:S2637")
    public Icon getIcon(@NotNull final PsiElement element, final int flags) {
        return Optional.of(element)
                .filter(PsiFile.class::isInstance)
                .map(PsiFile.class::cast)
                .map(PsiFile::getVirtualFile)
                .flatMap(virtualFile -> SCHEMA_FILE_PROVIDERS.stream()
                        .filter(GitHubSchemaProvider.class::isInstance)
                        .map(GitHubSchemaProvider.class::cast)
                        .filter(schemaProvider -> schemaProvider.isAvailable(virtualFile))
                        .map(schema -> AllIcons.Vcs.Vendors.Github)
                        .findFirst()
                )
                .orElse(null);
    }
}
