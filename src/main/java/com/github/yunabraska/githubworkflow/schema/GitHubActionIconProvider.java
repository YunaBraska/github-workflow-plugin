package com.github.yunabraska.githubworkflow.schema;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.github.yunabraska.githubworkflow.schema.GitHubSchemaProviderFactory.SCHEMA_FILE_PROVIDERS;

public class GitHubActionIconProvider extends IconProvider {

    @Nullable
    @Override
    public Icon getIcon(@NotNull final PsiElement element, final int flags) {
        if (element instanceof PsiFile) {
            final VirtualFile file = ((PsiFile) element).getVirtualFile();
            return SCHEMA_FILE_PROVIDERS.stream().anyMatch(schemaProvider -> schemaProvider.isAvailable(file))
                    ? AllIcons.Vcs.Vendors.Github
                    : null;
        }
        return null;
    }
}
