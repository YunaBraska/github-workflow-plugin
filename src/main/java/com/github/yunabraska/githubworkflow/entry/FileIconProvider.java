package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.helper.GitHubWorkflowHelper;
import com.github.yunabraska.githubworkflow.helper.PsiElementHelper;
import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.nio.file.Path;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.entry.SchemaProvider.SCHEMA_FILE_PROVIDERS;

public class FileIconProvider extends IconProvider {
    // IconLoader automatically resolves /icons/gitea_dark.svg in dark themes.
    private static final Icon GITEA_ICON = IconLoader.getIcon("/icons/gitea.svg", FileIconProvider.class);
    private static final String GITEA_WORKFLOW_HOME = ".gitea";

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
                        .map(schema -> iconFor(virtualFile))
                        .findFirst()
                )
                .orElse(null);
    }

    private static Icon iconFor(final VirtualFile virtualFile) {
        return isGiteaWorkflowFile(virtualFile) ? GITEA_ICON : AllIcons.Vcs.Vendors.Github;
    }

    private static boolean isGiteaWorkflowFile(final VirtualFile virtualFile) {
        return PsiElementHelper.toPath(virtualFile)
                .filter(GitHubWorkflowHelper::isWorkflowFile)
                .filter(FileIconProvider::isGiteaWorkflowPath)
                .isPresent();
    }

    private static boolean isGiteaWorkflowPath(final Path path) {
        return path.getName(path.getNameCount() - 3).toString().equalsIgnoreCase(GITEA_WORKFLOW_HOME);
    }
}
