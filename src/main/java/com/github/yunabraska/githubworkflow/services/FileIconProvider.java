package com.github.yunabraska.githubworkflow.services;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.github.yunabraska.githubworkflow.services.SchemaProvider.SCHEMA_FILE_PROVIDERS;

public class FileIconProvider extends IconProvider {

    @Nullable
    @Override
    public Icon getIcon(@NotNull final PsiElement element, final int flags) {
        if (element instanceof final PsiFile psiFile) {
            final VirtualFile file = psiFile.getVirtualFile();
            return SCHEMA_FILE_PROVIDERS.stream().anyMatch(schemaProvider -> schemaProvider.isAvailable(file))
                    ? AllIcons.Vcs.Vendors.Github
                    : null;
        }
        return null;
    }
}
